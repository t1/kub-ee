package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.model.*;
import org.junit.Test;

import java.io.*;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

public class ClusterListMessageBodyWriterTest {
    private ClusterListMessageBodyWriter writer = new ClusterListMessageBodyWriter();

    @Test
    public void shouldWriteHtml() throws Exception {
        List<Cluster> clusters = ClusterTest.readClusterConfig().clusters();
        OutputStream out = new ByteArrayOutputStream();

        writer.writeTo(clusters, null, null, null, null, null, out);

        assertThat(out.toString()).isEqualTo(""
                + "<!DOCTYPE html>\n"
                + "<html>\n"
                + "<head>\n"
                + "    <title>Meta-Deployer</title>\n"
                + "    <meta charset=\"utf-8\" />\n"
                + "    <link rel='stylesheet' href=\"http://maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css\" />\n"
                + "</head>\n"
                + "<body>\n"
                + "<div class=\"table-responsive\">\n"
                + "    <table class=\"table table-striped cluster-table\">\n"
                + "        <tr>\n"
                + "            <th>Cluster</th>\n"
                + "            <th class=\"stage\">DEV</th>\n"
                + "            <th class=\"stage\">QA</th>\n"
                + "            <th class=\"stage\">PROD</th>\n"
                + "            <th class=\"stage\"></th>\n"
                + "        </tr>\n"
                + "            <tr>\n"
                + "                <th>my.boss:8080</th>\n"
                + "                <td>\n"
                + "                    prefix: ''<br>\n"
                + "                    suffix: 'dev'<br>\n"
                + "                    count: 1<br>\n"
                + "                    indexLength: 2\n"
                + "                </td>\n"
                + "                <td>\n"
                + "                    prefix: 'qa'<br>\n"
                + "                    suffix: ''<br>\n"
                + "                    count: 1<br>\n"
                + "                    indexLength: 0\n"
                + "                </td>\n"
                + "                <td>\n"
                + "                    prefix: ''<br>\n"
                + "                    suffix: ''<br>\n"
                + "                    count: 3<br>\n"
                + "                    indexLength: 2\n"
                + "                </td>\n"
                + "                <td>\n"
                + "                    -\n"
                + "                </td>\n"
                + "            </tr>\n"
                + "            <tr>\n"
                + "                <th>other.boss:80</th>\n"
                + "                <td>\n"
                + "                    prefix: ''<br>\n"
                + "                    suffix: ''<br>\n"
                + "                    count: 2<br>\n"
                + "                    indexLength: 0\n"
                + "                </td>\n"
                + "                <td>\n"
                + "                    -\n"
                + "                </td>\n"
                + "                <td>\n"
                + "                    -\n"
                + "                </td>\n"
                + "                <td>\n"
                + "                    -\n"
                + "                </td>\n"
                + "            </tr>\n"
                + "            <tr>\n"
                + "                <th>third.boss:80</th>\n"
                + "                <td>\n"
                + "                    -\n"
                + "                </td>\n"
                + "                <td>\n"
                + "                    -\n"
                + "                </td>\n"
                + "                <td>\n"
                + "                    -\n"
                + "                </td>\n"
                + "                <td>\n"
                + "                    prefix: ''<br>\n"
                + "                    suffix: ''<br>\n"
                + "                    count: 1<br>\n"
                + "                    indexLength: 0\n"
                + "                </td>\n"
                + "            </tr>\n"
                + "    </table>\n"
                + "</div>\n"
                + "</body>\n"
                + "</html>\n");
    }
}
