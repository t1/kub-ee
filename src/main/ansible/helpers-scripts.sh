
sudo -u wildfly /opt/wildfly/bin/jboss-cli.sh --connect

sudo -u wildfly tail -f /opt/wildfly/standalone/log/server.log

curl http://localhost:8080/deployer -H'Accept: application/yaml'

curl -d "jolokia.version: 1.3.2" -X POST http://localhost:8080/deployer -H'Content-Type: application/yaml' -H'Accept: application/yaml'

sudo touch /opt/wildfly/standalone/deployments/deployer.war
