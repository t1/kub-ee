package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.model.*;
import com.github.t1.metadeployer.model.Deployment.DeploymentBuilder;
import org.junit.Test;

import java.io.*;
import java.net.URL;
import java.util.List;

import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;

public class DeploymentListHtmlMessageBodyWriterTest {
    private static final String EVENTS = " draggable=\"true\" ondragstart=\"drag_start(event);\" "
            + "ondragend=\"drag_end(event);\" ondragenter=\"drag_enter(event);\" ondragover=\"drag_over(event);\" "
            + "ondragleave=\"drag_leave(event);\" ondrop=\"drop_handler(event);";
    private DeploymentListHtmlMessageBodyWriter writer = new DeploymentListHtmlMessageBodyWriter();

    private DeploymentBuilder createDeployment(String name) {
        return Deployment.builder()
                         .groupId(name + "-group")
                         .artifactId(name + "-art")
                         .type(name + "-type")
                         .name(name);
    }

    private DeploymentBuilder createDeployment() {
        return Deployment.builder().groupId("com.github.t1").type("war");
    }

    @Test
    public void shouldWriteSimple() throws Exception {
        Stage prod = Stage.builder().name("PROD").build();
        Cluster one = Cluster.builder().host("localhost")
                             .slot(Slot.builder().name("1").http(1).build())
                             .stage(prod)
                             .build();
        Cluster two = Cluster.builder().host("localhost")
                             .slot(Slot.builder().name("2").http(2).build())
                             .stage(prod)
                             .build();
        writer.clusters = asList(one, two);
        ClusterNode prod1 = prod.index(one, 1);
        ClusterNode prod2 = prod.index(two, 1);

        DeploymentBuilder dummy = createDeployment().artifactId("dummy").name("dummy").version("1.2.3");
        List<Deployment> deployables = asList(dummy.node(prod1).build(), dummy.node(prod2).build());
        OutputStream out = new ByteArrayOutputStream();

        writer.writeTo(deployables, null, null, null, null, null, out);

        URL expected = DeploymentListHtmlMessageBodyWriterTest.class.getResource("deployment-list-expected.html");
        assertThat(out.toString()).isEqualTo(""
                + "<!DOCTYPE html>\n"
                + "<html>\n"
                + " <head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <title>Meta-Deployer</title>\n"
                + "  <link rel=\"stylesheet\" href=\"http://maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css\">\n"
                + "  <link rel=\"stylesheet\" href=\"/style.css\">\n"
                + "  <script type=\"text/javascript\" src=\"/script.js\"></script>\n"
                + " </head>\n"
                + " <body>\n"
                + "  <div class=\"table-responsive\">\n"
                + "   <table class=\"table table-striped\" id=\"deployables\">\n"
                + "    <tbody>\n"
                + "     <tr>\n"
                + "      <th>Cluster</th>\n"
                + "      <th>Application</th>\n"
                + "      <th class=\"stage\" colspan=\"1\">PROD</th>\n"
                + "     </tr>\n"
                + "     <tr>\n"
                + "      <th></th>\n"
                + "      <th></th>\n"
                + "      <th class=\"node\"></th>\n"
                + "     </tr>\n"
                + "     <tr>\n"
                + "      <th rowspan=\"1\">localhost:1</th>\n"
                + "      <th class=\"deployable-name\">dummy</th>\n"
                + "      <td>\n"
                + "       <div class=\"deployment\" id=\"localhost:1:PROD:1:dummy\"" + EVENTS + "\">\n"
                + "        1.2.3\n"
                + "       </div></td>\n"
                + "     </tr>\n"
                + "     <tr>\n"
                + "      <th rowspan=\"1\">localhost:2</th>\n"
                + "      <th class=\"deployable-name\">dummy</th>\n"
                + "      <td>\n"
                + "       <div class=\"deployment\" id=\"localhost:2:PROD:1:dummy\"" + EVENTS + "\">\n"
                + "        1.2.3\n"
                + "       </div></td>\n"
                + "     </tr>\n"
                + "    </tbody>\n"
                + "   </table>\n"
                + "  </div>\n"
                + " </body>\n"
                + "</html>");
    }

    @Test
    public void shouldWriteFull() throws Exception {
        writer.clusters = ClusterTest.readClusterConfig().clusters();

        Cluster my = writer.clusters.get(0);
        ClusterNode my_dev_1 = my.getStages().get(0).index(my, 1);
        ClusterNode my_qa_1 = my.getStages().get(1).index(my, 1);
        ClusterNode my_prod_1 = my.getStages().get(2).index(my, 1);
        ClusterNode my_prod_2 = my.getStages().get(2).index(my, 2);
        ClusterNode my_prod_3 = my.getStages().get(2).index(my, 3);

        Cluster other = writer.clusters.get(1);
        ClusterNode other_dev_1 = other.getStages().get(0).index(other, 1);
        ClusterNode other_dev_2 = other.getStages().get(0).index(other, 2);

        DeploymentBuilder foo = createDeployment("foo");
        DeploymentBuilder bar = createDeployment("bar");
        DeploymentBuilder baz = createDeployment("baz");
        List<Deployment> deployables = asList(
                foo.node(my_dev_1).version("1.2.5-SNAPSHOT").build(),
                foo.node(my_qa_1).version("1.2.4").build(),
                foo.node(my_prod_1).version("1.2.3").build(),
                foo.node(my_prod_2).version("1.2.3").build(),
                foo.node(my_prod_3).version("1.2.3").build(),
                bar.node(other_dev_1).version("2.0.1").build(),
                bar.node(other_dev_2).version("2.0.2").build(),
                baz.node(other_dev_2).version("2.1.3").error("error-hint").build());
        OutputStream out = new ByteArrayOutputStream();

        writer.writeTo(deployables, null, null, null, null, null, out);

        URL expected = DeploymentListHtmlMessageBodyWriterTest.class.getResource("deployment-list-expected.html");
        assertThat(out.toString()).isEqualTo(contentOf(expected).trim());
    }
}
