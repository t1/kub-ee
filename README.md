# Kub-EE

Manage the applications on multiple Java EE clusters.

## User Journey

1. Tom is responsible for a pool of JBoss machines and the applications running there.
   They are DNS-named `worker-qa-01`, `worker-qa-02`, `worker-01`, and `worker-02`. _(done)_
1. He deploys [nginx](http://nginx.org) and Kub-EE on machines that act as load balancers (LB):
   `worker-qa` and `worker`. _(done)_
1. He deploys [The Deployer](https://github.com/t1/deployer) on the other JBoss machines and
    * configures the clusters, slots, nodes, and stages in Kub-EE _(done)_, or
    * configures The Deployers to report to Kub-EE instance, including their stage. _(todo)_
1. He browses to Kub-EE and sees a matrix like this:
<table>
 <tbody>
  <tr>
   <th rowspan="2">Cluster</th>
   <th rowspan="2">Application</th>
   <th colspan="2">QA</th>
   <th colspan="2">PROD</th>
  </tr>
  <tr>
   <th>01</th>
   <th>02</th>
   <th>01</th>
   <th>02</th>
  </tr>
  <tr>
   <th rowspan="2">worker:8080</th>
   <th>deployer</th>
   <td>2.9.2</td>
   <td>2.9.2</td>
   <td>2.9.2</td>
   <td>2.9.2</td>
  </tr>
  <tr>
   <th>jolokia</th>
   <td>1.3.6</td>
   <td>1.3.5</td>
   <td>1.3.5</td>
   <td>1.3.5</td>
  </tr>
 </tbody>
</table>

   I.e. `Jolokia` is deployed in version `1.3.6` on `http://qa-worker-01:8080`, which is a node in the `QA` stage
   (the prefix `qa-` is part of the cluster config and not visible here). _(done)_
5. If he doesn't have the rights to (un)deploy to a node, this is all he can do. _(todo)_
1. He opens a menu in one matrix cell and picks `undeploy`: The version gets undeployed,
   after it's been removed from the LB nginx config (incl. reload and all current requests finishing). _(done)_
1. For an application `foo` with a variable in the deployer named `foo.version` (typically on DEV stages) _(todo)_
   he opens a matrix cell menu and picks a different version which gets deployed
   after the node is removed from the app LB, and re-added after the update. _(done)_
1. Before the undeploy and a second time after a deploy but before adding to the LB,
   a health check is done. If the deploy made things worse, the last version is restored. _(todo)_
1. If the deployments to a stage are done with a CI/CD pipeline, this menu only contains the current
   and the fallback version for emergency rollbacks. _(todo)_
1. He option-drags a cell from one machine to another and it gets deployed on the target and added to the LB. _(done)_
1. He drags a cell from one machine to another and it gets deployed on the target and undeployed on the source;
   the LB is updated accordingly. _(done)_
1. He switches to a _compact_ view that collapses the node indexes on the top so he only sees the stages;
   the cells show the number of instances of an application on this stage;
   if there are different versions on the nodes of one stage, both are displayed. _(todo)_
1. He clicks on a `+` button next to the number, and another instance is deployed;
   Kub-EE picks the node with the lowest load. _(todo)_
1. He clicks on a `-` button and one instance is undeployed;
   Kub-EE picks the application instance running on the node with the highest load. _(todo)_
1. He opens a menu in one matrix cell and picks a different version;
   he sees an in-place option to deploy the new version on all nodes (one-by-one) or only on one node; _(todo)_
1. He chooses to update all, and sees the numbers of the different versions within the cell as the rollout progresses. _(todo)_
1. He configures a minimum and maximum load for one service on one stage,
   and Kub-EE automatically deploys or undeploys instances on more or less nodes to stay in that load range. _(todo)_


## Conventions

We follow the convention-over-configuration principle, so things simply work for a standard setup.
If following these conventions do not suffice for a special case, we'll have to add a configuration point,
but doing so prematurely would add extra complexity we'd rather like to avoid before we have such a real-world use case.

1. An application `foo` can be (un)deployed with `foo.state` (`deployed` or `undeployed`).
1. A different version of an application `foo` can be deployed with `foo.version`.
1. The versions of an application `foo` can be found (by The Deployer) by using the deployment name on the node
   to retrieve the group- and artifact-id.
1. On a QA stage with the suffix `-qa`, an application `foo` is proxied to `http://foo-qa-lb/foo`,
   where `foo-qa-lb` is the application load-balancer. 
1. The nginx config file is in `/usr/local/etc/nginx` and has the prefix and suffix of its stage, e.g. `nginx-qa.conf`.

## Kubernetes Equivalents

Some Kub-EE concepts match quite well to the concepts of [Kubernetes](https://kubernetes.io).
Here's how to translate the terms:

| Kubernetes | Kub-EE | Comment |
| --- | --- | --- |
| Pod | Bundle | Bundles can be nested and provide live templating, which Pods don't. |
| Kubelet | The Deployer | The Deployer is unaware of Kub-EE |
| Worker/Docker Host | JEE Container | Multiple JEE containers can run on one machine by using a different port offset (typically by 100s), called Slots |


## Ideas For The Deployer

* Health and load metrics; by app & total
* Web-Sockets to push audits (maybe even watch others deploy/undeploy)
