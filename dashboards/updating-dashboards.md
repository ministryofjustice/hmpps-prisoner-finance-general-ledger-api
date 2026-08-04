# Updating the dashboards via Azure

## If you have updated the dashboards via Azure you must do the follow to update the other environments

In the Azure UI dashboard "Prod" click Export->download to download the file as .json

Once downloaded you should copy the file and rename for the Pre-prod environment.

You then need to replace any instances of "nomisapi-prod-rg" and replace to "nomisapi-preprod-rg"

And the workspaces need to be updated from "workspaces/nomisapi-prod" to "orkspaces/nomisapi-preprod"

And finally update the dashboard name from:-

"name": "Prisoner Finance - Prod - Dashboard",
"hidden-title": "Prisoner Finance - Prod - Dashboard"

to 

"name": "Prisoner Finance - PreProd - Dashboard",
"hidden-title": "Prisoner Finance - PreProd - Dashboard"

You should then re "Upload" the .json to the PreProd environment. You should also do the same for the Dev environment / dashboard if you wish to keep this aligned as well.

Please see this ticket if about deploying the dashboards via a GitHub pipeline.
https://dsdmoj.atlassian.net/browse/PFI-1420
