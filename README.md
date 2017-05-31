# Meta-Deployer

Manage the applications on multiple Java EE clusters.

## User Journey

* Tom is responsible for a pool of JBoss machines and the applications running there.
* He deploys nginx and the Meta-Deployer on a machine that acts as a load balancer.
* He deploys [The Deployer](https://github.com/t1/deployer) on the other JBoss machines and configures them to report to the Meta-Deployer, including their stage.
* He browses to the Meta-Deployer and sees a matrix of all containers and all running applications showing their versions and load.
* He opens a menu in one matrix cell and picks a different version which gets deployed immediately.
* He picks `undeploy` and it gets undeployed and removed from the LB nginx config, which gets reloaded.
* He option-drags a cell from one machine to another and it gets deployed on the target and added to the LB.
* He drags a cell from one machine to another and it gets deployed on the target and undeployed on the source; the LB is updated accordingly.
* He switches to a _compact_ view that collapses the node indexes on the top so he only sees the stages;
  the cells show the number of instances of an application on this stage;
  if there are different versions on the nodes of one stage, both are displayed.
* He clicks on a `+` button next to the number, and another instance is deployed;
  the meta-deployer picks the node with the lowest load.
* He clicks on a `-` button and one instance is undeployed;
  the meta-deployer picks the application instance running on the node with the most load.
* He opens a menu in one matrix cell and picks a different version;
  he sees an in-place option to deploy the new version on all nodes (one-by-one) or only on one node;
* He decides to update all, and sees the numbers of the different versions within the cell as the rollout progresses.
* He configures a minimum and maximum load for one service on one stage,
  and the meta deployer deploys or undeploys instances on more or less nodes to stay in that load range.


## New Requirements For The Deployer

* Report to meta-deployer:
  * Web-Sockets to push audits
  * Load Metrics; by app & total
  * Put root bundle
* Deployables _containing_ config bundle, or matching config bundle GAV with classifier `bundle`.
