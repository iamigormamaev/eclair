package ru.tinkoff.integration.eclair.format.printer;

import org.springframework.oxm.jaxb.Jaxb2Marshaller;

import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

/**
 * xml -> throws
 *
 * @author Viacheslav Klapatniuk
 */
public class Jaxb2Printer extends Printer {

    final Jaxb2Marshaller jaxb2Marshaller;

    public Jaxb2Printer(Jaxb2Marshaller jaxb2Marshaller) {
        this.jaxb2Marshaller = jaxb2Marshaller;
    }

    @Override
    public String serialize(Object input) {
        StringWriter writer = new StringWriter();
        jaxb2Marshaller.marshal(input, new StreamResult(writer));
        return writer.toString();
    }
}
