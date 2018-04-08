package ru.tinkoff.eclair.aop;

import org.slf4j.LoggerFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.ClassUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import ru.tinkoff.eclair.core.*;
import ru.tinkoff.eclair.definition.*;
import ru.tinkoff.eclair.definition.factory.MethodLogFactory;
import ru.tinkoff.eclair.definition.factory.MethodMdcFactory;
import ru.tinkoff.eclair.logger.EclairLogger;
import ru.tinkoff.eclair.printer.Printer;
import ru.tinkoff.eclair.validate.BeanClassValidator;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * @author Viacheslav Klapatniuk
 */
public class EclairProxyCreator extends AbstractAutoProxyCreator {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(EclairProxyCreator.class);
    private static final Object[] EMPTY_ARRAY = new Object[0];

    private final Map<String, Class<?>> beanClassCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object[]> advisorsCache = new ConcurrentHashMap<>();

    private final Map<String, EclairLogger> loggers;
    private final GenericApplicationContext applicationContext;
    private final BeanClassValidator beanClassValidator;
    private final AnnotationDefinitionFactory annotationDefinitionFactory;
    private final StandardEvaluationContext standardEvaluationContext;
    private final ExpressionParser expressionParser;

    private final LoggerBeanNamesResolver loggerBeanNamesResolver = LoggerBeanNamesResolver.getInstance();
    private final AnnotationExtractor annotationExtractor = AnnotationExtractor.getInstance();
    private final ParameterNameResolver parameterNameResolver = new ParameterNameResolver();

    private boolean validate = false;

    public EclairProxyCreator(List<Printer> printers,
                              Map<String, EclairLogger> loggers,
                              GenericApplicationContext applicationContext,
                              BeanClassValidator beanClassValidator) {
        this.annotationDefinitionFactory = new AnnotationDefinitionFactory(new PrinterResolver(applicationContext, printers));
        this.loggers = initLoggers(loggers);
        this.applicationContext = applicationContext;
        this.beanClassValidator = beanClassValidator;
        this.standardEvaluationContext = new StandardEvaluationContext();
        this.standardEvaluationContext.setBeanResolver(new BeanFactoryResolver(applicationContext.getBeanFactory()));
        this.expressionParser = new SpelExpressionParser(new SpelParserConfiguration(SpelCompilerMode.MIXED, null));
    }

    private Map<String, EclairLogger> initLoggers(Map<String, EclairLogger> loggers) {
        return loggers.entrySet().stream()
                .sorted(comparing(Map.Entry::getValue, AnnotationAwareOrderComparator.INSTANCE))
                .collect(toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (logger, logger2) -> logger,
                        LinkedHashMap::new
                ));
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        Class<?> targetClass = (bean instanceof FactoryBean) ? ((FactoryBean) bean).getObjectType() : ClassUtils.getUserClass(bean);
        beanClassCache.putIfAbsent(beanName, targetClass);
        return bean;
    }

    @Override
    protected Object[] getAdvicesAndAdvisorsForBean(Class<?> beanClass, String beanName, TargetSource customTargetSource) throws BeansException {
        Class<?> targetClass = beanClassCache.computeIfAbsent(beanName, s -> ClassUtils.getUserClass(beanClass));
        Object[] cachedAdvisors = advisorsCache.get(targetClass);
        if (nonNull(cachedAdvisors)) {
            return cachedAdvisors.length == 0 ? AbstractAutoProxyCreator.DO_NOT_PROXY : cachedAdvisors;
        }
        if (!beanClassValidator.supports(targetClass)) {
            advisorsCache.put(targetClass, EMPTY_ARRAY);
            return AbstractAutoProxyCreator.DO_NOT_PROXY;
        }
        if (validate) {
            validateBeanClass(targetClass, beanName);
        }
        MdcAdvisor mdcAdvisor = getMdcAdvisor(targetClass);
        List<LogAdvisor> logAdvisors = getLoggingAdvisors(targetClass);
        Object[] composedAdvisors = composeAdvisors(mdcAdvisor, logAdvisors);
        advisorsCache.put(targetClass, composedAdvisors);
        return composedAdvisors.length == 0 ? AbstractAutoProxyCreator.DO_NOT_PROXY : composedAdvisors;
    }

    private void validateBeanClass(Class<?> beanClass, String beanName) {
        BindingResult bindingResult = new BeanPropertyBindingResult(beanClass, beanName);
        beanClassValidator.validate(beanClass, bindingResult);
        if (bindingResult.hasErrors()) {
            bindingResult.getAllErrors()
                    .forEach(objectError -> logger.error(objectError.getDefaultMessage()));
            throw new BeanInstantiationException(beanClass, "Incorrect logging annotations usage", new BindException(bindingResult));
        }
    }

    @Override
    protected boolean advisorsPreFiltered() {
        return true;
    }

    private MdcAdvisor getMdcAdvisor(Class<?> beanClass) {
        List<MethodMdc> methodMdcs = annotationExtractor.getCandidateMethods(beanClass).stream()
                .map(this::getMethodMdc)
                .filter(Objects::nonNull)
                .collect(toList());
        return MdcAdvisor.newInstance(expressionParser, standardEvaluationContext, methodMdcs);
    }

    private MethodMdc getMethodMdc(Method method) {
        List<String> parameterNames = parameterNameResolver.tryToResolve(method);
        Set<ParameterMdc> methodParameterMdcs = annotationDefinitionFactory.buildMethodParameterMdcs(method);
        List<Set<ParameterMdc>> parameterMdcs = annotationDefinitionFactory.buildParameterMdcs(method);
        return MethodMdcFactory.newInstance(method, parameterNames, methodParameterMdcs, parameterMdcs);
    }

    private List<LogAdvisor> getLoggingAdvisors(Class<?> beanClass) {
        return loggers.entrySet().stream()
                .map(entry -> getLogAdvisor(beanClass, entry.getKey(), entry.getValue()))
                .filter(Objects::nonNull)
                .collect(toList());
    }

    private LogAdvisor getLogAdvisor(Class<?> beanClass, String loggerName, EclairLogger eclairLogger) {
        List<MethodLog> methodLogs = getMethodLogs(beanClass, loggerName);
        return LogAdvisor.newInstance(eclairLogger, methodLogs);
    }

    private List<MethodLog> getMethodLogs(Class<?> beanClass, String loggerName) {
        Set<String> loggerNames = loggerBeanNamesResolver.resolve(applicationContext, loggerName);
        return annotationExtractor.getCandidateMethods(beanClass).stream()
                .map(method -> getMethodLog(loggerNames, method))
                .filter(Objects::nonNull)
                .collect(toList());
    }

    private MethodLog getMethodLog(Set<String> loggerNames, Method method) {
        List<String> parameterNames = parameterNameResolver.tryToResolve(method);
        InLog inLog = annotationDefinitionFactory.buildInLog(loggerNames, method);
        List<ParameterLog> parameterLogs = annotationDefinitionFactory.buildParameterLogs(loggerNames, method);
        OutLog outLog = annotationDefinitionFactory.buildOutLog(loggerNames, method);
        Set<ErrorLog> errorLogs = annotationDefinitionFactory.buildErrorLogs(loggerNames, method);
        return MethodLogFactory.newInstance(method, parameterNames, inLog, parameterLogs, outLog, errorLogs);
    }

    private Object[] composeAdvisors(MdcAdvisor mdcAdvisor, List<LogAdvisor> logAdvisors) {
        if (isNull(mdcAdvisor)) {
            return logAdvisors.toArray();
        }
        List<Advisor> advisors = new ArrayList<>(singletonList(mdcAdvisor));
        advisors.addAll(logAdvisors);
        return advisors.toArray();
    }

    public void setValidate(boolean validate) {
        this.validate = validate;
    }

    Map<String, Class<?>> getBeanClassCache() {
        return beanClassCache;
    }

    @Override
    protected void customizeProxyFactory(ProxyFactory proxyFactory) {
        proxyFactory.setProxyTargetClass(true);
    }
}
