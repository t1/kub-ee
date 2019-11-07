import \
    os

import \
    testinfra.utils.ansible_runner

# TODO complete README.md

testinfra_hosts = testinfra.utils.ansible_runner.\
    AnsibleRunner(os.environ['MOLECULE_INVENTORY_FILE']).\
    get_hosts('all')


def test_hosts_file(host):
    f = host.file('/etc/hosts')

    assert f.exists
    assert f.user == 'root'
    assert f.group == 'root'


def test_wildfly_is_installed(host):
    assert host.package('java-1.8.0-openjdk-headless').is_installed
    host.file('/opt/wildfly-9.0.1.Final/bin/standalone.sh')


# TODO implement
# def test_wildfly_is_running(host):
#     process = host.process.get(user="root", comm="wildfly")
