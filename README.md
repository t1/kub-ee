# Kub-EE

Manage the applications on multiple Java EE clusters.

## User Journey

1. Tom is responsible for a pool of JBoss machines and the applications running there. _(done)_
1. He deploys nginx and the Kub-EE on a machine that acts as a load balancer (LB). _(done)_
1. He deploys [The Deployer](https://github.com/t1/deployer) on the other JBoss machines and
    * configures the clusters, slots, nodes, and stages in the Kub-EE, or _(done)_
    * configures The Deployers to report to the Kub-EE instance, including their stage. _(todo)_
1. He browses to the Kub-EE and sees a matrix like this:
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

   I.e. `Jolokia` is deployed in version `1.3.6` on `http://qa-worker01:8080`, which is a node in the `QA` stage
   (the prefix `qa-` is part of the cluster config and not visible here). _(done)_
5. He opens a menu in one matrix cell and picks `undeploy`: The version gets undeployed,
   after it's been removed from the LB nginx config (incl. reload and all current requests finishing). _(done)_
1. He opens a menu in another matrix cell and picks a different version which gets deployed,
   while the LB is changed and updated before the undeploy of the old version and after the deploy of the new version. _(done)_
1. He option-drags a cell from one machine to another and it gets deployed on the target and added to the LB. _(done)_
1. He drags a cell from one machine to another and it gets deployed on the target and undeployed on the source;
   the LB is updated accordingly, i.e. once after it's been deployed to the new machine. _(done)_
1. He switches to a _compact_ view that collapses the node indexes on the top so he only sees the stages;
   the cells show the number of instances of an application on this stage;
   if there are different versions on the nodes of one stage, both are displayed. _(todo)_
1. He clicks on a `+` button next to the number, and another instance is deployed;
   Kub-EE picks the node with the lowest load. _(todo)_
1. He clicks on a `-` button and one instance is undeployed;
   Kub-EE picks the application instance running on the node with the most load. _(todo)_
1. He opens a menu in one matrix cell and picks a different version;
   he sees an in-place option to deploy the new version on all nodes (one-by-one) or only on one node; _(todo)_
1. He decides to update all, and sees the numbers of the different versions within the cell as the rollout progresses. _(todo)_
1. He configures a minimum and maximum load for one service on one stage,
   and Kub-EE deploys or undeploys instances on more or less nodes to stay in that load range. _(todo)_


## Ideas For The Deployer

* Web-Sockets to push audits (maybe even watch others deploy/undeploy)
* Load metrics; by app & total
