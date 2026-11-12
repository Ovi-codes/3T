# Azure infrastructure

`main.bicep` provisions everything the app needs in one resource group. Why it looks like this:
[ADR 0002](../docs/adr/0002-single-container-on-container-apps.md).

| Resource | Setting | Cost |
| :--- | :--- | :--- |
| Postgres Flexible Server | B1MS Burstable, PG 16, 32 GB, auto-grow off, no HA, no geo-backup, database `runro` | Free for 12 months (750 h + 32 GB), then ~€13/month |
| Container Apps environment | Consumption, no Log Analytics workspace | €0 |
| Container App `3t-run` | 0.5 vCPU / 1 GiB, 0–1 replicas, HTTPS ingress on 8080, probes on `/actuator/health` | €0 inside the monthly free grant |

The image comes from GHCR (`ghcr.io/ovi-codes/3t`), which is free for a public package.
`deploy.yml` builds it after green CI on `main` and rolls it out.

## One-time setup

Do these in order. You need the Azure CLI (`az`, with Bicep: `az bicep install`) and `gh`.

### 1. Cost guard

In the portal, go to **Cost Management → Budgets** and create a monthly budget on the subscription
(€1–5) with email alerts at 50% and 100%.

### 2. Resource providers and resource group

```sh
az login
az provider register --namespace Microsoft.App
az provider register --namespace Microsoft.DBforPostgreSQL
az provider register --namespace Microsoft.Communication
az group create --name rg-3t-run --location northeurope   # or swedencentral; keep it in the EU
```

If B1MS is not offered in that region, pick another EU region and use it for everything.

### 3. First image

Merge to `main`. When CI is green, `deploy.yml` pushes `ghcr.io/ovi-codes/3t:<sha>` and `:main`.
The `deploy` job is skipped because `AZURE_CONTAINER_APP` is not set yet. That is expected.

Then, on GitHub, go to **Packages → 3t → Package settings** and set the visibility to **Public**,
so Container Apps can pull the image without credentials.

### 4. Provision

```sh
az deployment group create \
  --resource-group rg-3t-run \
  --template-file infra/main.bicep \
  --parameters dbAdminPassword='<strong password>' adminEmails='you@example.com'
```

The outputs give `appUrl` and `postgresHost`. In the portal, check that the Postgres server shows
the **B1MS** SKU. Open `appUrl`: the first load waits for the cold start, and then the site is up.
Registration emails cannot be sent until step 5.

### 5. Email (Azure Communication Services)

1. Create an **Email Communication Service** with data location **Europe**, and add an **Azure
   Managed Domain**. That gives a sender like `DoNotReply@<id>.azurecomm.net`.
2. Create a **Communication Services** resource (data location Europe) and connect the domain to it
   (**Email → Domains → Connect domain**).
3. Create an **Entra app registration** with a client secret. On the Communication Services
   resource, give that app a role that can send email (for example *Communication and Email Service
   Owner*).
4. On the Communication Services resource, go to **Settings → SMTP Usernames** and create a username
   for that app.
5. Re-run step 4 with the mail parameters. Pass the running image so the re-run doesn't roll back
   to `:main`:

```sh
image=$(az containerapp show -g rg-3t-run -n 3t-run --query "properties.template.containers[0].image" -o tsv)
az deployment group create \
  --resource-group rg-3t-run \
  --template-file infra/main.bicep \
  --parameters dbAdminPassword='<same password>' adminEmails='you@example.com' \
    containerImage="$image" \
    mailHost=smtp.azurecomm.net mailUsername='<SMTP username>' \
    mailPassword='<client secret>' mailFrom='DoNotReply@<id>.azurecomm.net'
```

The managed domain has low send limits. That is fine for a demo.

### 6. GitHub → Azure login (OIDC)

1. Create an Entra app registration (or a user-assigned managed identity) for GitHub Actions.
2. Add a **federated credential**: GitHub Actions, repo `Ovi-codes/3T`, entity **Environment**,
   name `production` (subject `repo:Ovi-codes/3T:environment:production`).
3. Give it **Contributor on the resource group only**:
   `az role assignment create --assignee <client id> --role Contributor --scope $(az group show -n rg-3t-run --query id -o tsv)`
4. On GitHub:

```sh
gh api -X PUT repos/Ovi-codes/3T/environments/production
gh secret set AZURE_CLIENT_ID --body '<client id>'
gh secret set AZURE_TENANT_ID --body '<tenant id>'
gh secret set AZURE_SUBSCRIPTION_ID --body '<subscription id>'
gh variable set AZURE_RESOURCE_GROUP --body rg-3t-run
gh variable set AZURE_CONTAINER_APP --body 3t-run
```

Then run **Actions → Deploy (Azure) → Run workflow**. It rolls out the image and waits for
`/actuator/health` to report `UP`.

### 7. Clean up the old skeleton

If you created `AZURE_WEBAPP_PUBLISH_PROFILE`, `AZURE_STATIC_WEB_APPS_API_TOKEN` or
`AZURE_WEBAPP_NAME` for the old App Service workflow, delete them.

## Operating it

- **Logs:** `az containerapp logs show -g rg-3t-run -n 3t-run --follow`
- **Roll back:** `az containerapp update -g rg-3t-run -n 3t-run --image ghcr.io/ovi-codes/3t:<older sha>`
- **No cold start (costs money):** `az containerapp update -g rg-3t-run -n 3t-run --min-replicas 1`
- **Cost:** check Cost Management a few days after go-live. It should show ~€0.

## Settings the app reads

All config comes from env vars (`backend/src/main/resources/application.yml`). The Bicep sets
`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` (secret), `DB_SSLMODE=require`,
`ADMIN_EMAILS`, and after step 5 `MAIL_HOST`, `MAIL_PORT`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS`,
`MAIL_USERNAME`, `MAIL_PASSWORD` (secret) and `MAIL_FROM`. `DEMO_DATA` is never set in prod.
