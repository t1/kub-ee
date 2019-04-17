# Kub-EE

Manage the applications on multiple Java EE clusters.

## User Journey

1. Tom is responsible for a pool of JBoss machines and the applications running there.
   They are DNS-named `worker-qa-01`, `worker-qa-02`, `worker-01`, and `worker-02`. _(done)_
1. He deploys [nginx](http://nginx.org) and Kub-EE on a machine that acts as load balancers (LB). _(done)_
1. He deploys [The Deployer](https://github.com/t1/deployer) on the worker JBoss machines and
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
1. He opens a menu in one matrix cell and picks `unbalance`:
    * The node is removed from the app LB (incl. a reload). _(done)_
    * All currently running requests are finished (JBoss feature).
    * The matrix cell shows an icon to indicate that it's not in the LB. _(done)_
1. He opens a menu in a unbalanced matrix cell and picks `balance`:
    * The node is added from the app LB (incl. a reload). _(done)_
    * The unbalanced icon is removed from the matrix cell. _(done)_
1. He opens a menu in one matrix cell and picks `undeploy`:
    * The node is removed from the app LB (incl. a reload). _(done)_
    * All currently running requests are finished (JBoss feature).
    * The application is undeployed. _(done)_
    * The version is removed from the matrix cell. _(done)_
    * If the app on the node was unbalanced, it's not any more. _(done)_
1. He opens a matrix cell menu and sees a list of all available versions.
   He picks a different version:
    * The health of the app on the node is recorded. _(done)_
    * The node is removed from the app LB (incl. a reload). _(done))
    * All currently running requests are finished (JBoss feature).
    * The new version is deployed (the Deployer pulls it from a maven repository). _(done)_
    * The health of the app on the node is checked again, and if it's gone from green to red, the previous version is restored. _(done)_
    * If the app on the node is not unbalanced, it's re-added to the app LB (incl. reload). _(done)_
1. If the deployments to a stage are done with a CI/CD pipeline, this menu only contains the current
   and the fallback version for rollbacks. _(todo)_
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
