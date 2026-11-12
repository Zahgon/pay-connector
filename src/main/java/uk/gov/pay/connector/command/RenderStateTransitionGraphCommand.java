package uk.gov.pay.connector.command;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import uk.gov.pay.connector.common.model.domain.PaymentGatewayStateTransitions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * The {@code render-state-transition-graph} command: outputs a representation of the connector state transitions as
 * a graphviz 'dot' file.
 */
@Configuration(proxyBeanMethods = false)
public class RenderStateTransitionGraphCommand implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String data = new StateTransitionGraphVizRenderer(PaymentGatewayStateTransitions.getInstance()).toString();

        Path path = Paths.get("states.dot");
        writeToFile(data, path);

        System.out.format("Wrote state transition graph to '%s'\n\n", path);
        System.out.println("Render using: ");
        System.out.println("  $ dot -Tpng -O states.dot");
        System.out.println();
        System.out.println("or upload to http://www.webgraphviz.com/");
    }

    private void writeToFile(String data, Path path) throws IOException {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        Files.write(path, bytes, StandardOpenOption.CREATE);
    }
}
