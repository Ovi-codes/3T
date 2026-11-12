// 3T Run on Azure at ~€0/month (ADR 0002). One deployment provisions:
//   - Postgres Flexible Server B1MS (12-month free services: 750 h + 32 GB)
//   - a Container Apps environment (consumption, no Log Analytics workspace)
//   - the Container App running the single image (Spring Boot + Angular), scale to zero
// To deploy or re-run, see infra/README.md. Re-running is safe: it converges to this file.

@description('Azure region for every resource. Keep it in the EU (GDPR, charter §7).')
param location string = resourceGroup().location

@description('Prefix for resource names.')
param namePrefix string = '3t-run'

@description('Image the Container App runs. The deploy workflow pins each release to a :<sha> tag; :main is the latest image built from main.')
param containerImage string = 'ghcr.io/ovi-codes/3t:main'

@description('Postgres admin login. The app connects with it.')
param dbAdminLogin string = 'runro'

@secure()
@description('Postgres admin password. Becomes a Container App secret.')
param dbAdminPassword string

@description('Comma-separated emails granted ROLE_ADMIN (ADMIN_EMAILS). Empty means no admins.')
param adminEmails string = ''

@description('SMTP host for confirmation emails, e.g. smtp.azurecomm.net. Leave empty until email is set up. Until then, registrations cannot send mail.')
param mailHost string = ''

@description('SMTP username (the ACS SMTP username).')
param mailUsername string = ''

@secure()
@description('SMTP password (the client secret of the Entra app behind the ACS SMTP username).')
param mailPassword string = ''

@description('From address on confirmation emails, e.g. DoNotReply@<id>.azurecomm.net.')
param mailFrom string = ''

var suffix = uniqueString(resourceGroup().id)
var mailEnabled = !empty(mailHost)
var appPort = 8080

// ── Postgres ────────────────────────────────────────────────────────────────
resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: '${namePrefix}-pg-${suffix}'
  location: location
  sku: {
    name: 'Standard_B1ms'
    tier: 'Burstable'
  }
  properties: {
    version: '16'
    administratorLogin: dbAdminLogin
    administratorLoginPassword: dbAdminPassword
    authConfig: {
      passwordAuth: 'Enabled'
      activeDirectoryAuth: 'Disabled'
    }
    // The free services cover 32 GB. Auto-grow is off, so storage never grows into a bill.
    storage: {
      storageSizeGB: 32
      autoGrow: 'Disabled'
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: {
      mode: 'Disabled'
    }
    network: {
      publicNetworkAccess: 'Enabled'
    }
  }
}

resource database 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
  parent: postgres
  name: 'runro'
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

// 0.0.0.0–0.0.0.0 is Azure's "allow Azure services" rule. Consumption Container Apps have no
// fixed outbound IP, so this is how the app reaches the server. The server only accepts TLS,
// and the password protects the rest. ADR 0002 has the private-network alternative.
resource allowAzureServices 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = {
  parent: postgres
  name: 'AllowAzureServices'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

// ── Container Apps ──────────────────────────────────────────────────────────
// No appLogsConfiguration: no Log Analytics workspace, so no log-ingest cost. The live log
// stream (az containerapp logs show --follow) still works.
resource environment 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: '${namePrefix}-env'
  location: location
  properties: {
    zoneRedundant: false
  }
}

var dbSecrets = [
  {
    name: 'db-password'
    value: dbAdminPassword
  }
]
var mailSecrets = mailEnabled && !empty(mailPassword)
  ? [
      {
        name: 'mail-password'
        value: mailPassword
      }
    ]
  : []

var appEnv = [
  { name: 'DB_HOST', value: postgres.properties.fullyQualifiedDomainName }
  { name: 'DB_PORT', value: '5432' }
  { name: 'DB_NAME', value: database.name }
  { name: 'DB_USER', value: dbAdminLogin }
  { name: 'DB_PASSWORD', secretRef: 'db-password' }
  { name: 'DB_SSLMODE', value: 'require' }
  { name: 'ADMIN_EMAILS', value: adminEmails }
  // DEMO_DATA is never set here. The demo seed is for local demos only.
]
var mailEnv = mailEnabled
  ? concat(
      [
        { name: 'MAIL_HOST', value: mailHost }
        { name: 'MAIL_PORT', value: '587' }
        { name: 'MAIL_SMTP_AUTH', value: 'true' }
        { name: 'MAIL_SMTP_STARTTLS', value: 'true' }
        { name: 'MAIL_USERNAME', value: mailUsername }
        { name: 'MAIL_FROM', value: mailFrom }
      ],
      empty(mailPassword) ? [] : [{ name: 'MAIL_PASSWORD', secretRef: 'mail-password' }]
    )
  : []

resource app 'Microsoft.App/containerApps@2024-03-01' = {
  name: namePrefix
  location: location
  properties: {
    managedEnvironmentId: environment.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: true
        targetPort: appPort
        transport: 'auto'
        allowInsecure: false
      }
      secrets: concat(dbSecrets, mailSecrets)
    }
    template: {
      containers: [
        {
          name: 'app'
          image: containerImage
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: concat(appEnv, mailEnv)
          probes: [
            // A cold start (JVM + Flyway) takes ~10–40 s. Allow up to 20 + 10 × 10 = 120 s.
            {
              type: 'Startup'
              httpGet: {
                path: '/actuator/health'
                port: appPort
              }
              initialDelaySeconds: 20
              periodSeconds: 10
              timeoutSeconds: 5
              failureThreshold: 10
            }
            // No traffic while Postgres is unreachable.
            {
              type: 'Readiness'
              httpGet: {
                path: '/actuator/health'
                port: appPort
              }
              periodSeconds: 15
              timeoutSeconds: 5
              failureThreshold: 3
            }
            // Restart only when the process itself is stuck, never because of the DB.
            {
              type: 'Liveness'
              httpGet: {
                path: '/actuator/health/liveness'
                port: appPort
              }
              periodSeconds: 30
              timeoutSeconds: 5
              failureThreshold: 3
            }
          ]
        }
      ]
      // Scale to zero after ~5 min idle. The next visit then waits for the cold start. One
      // replica is the €0 ceiling. The soak (#89) raises it for its run only.
      scale: {
        minReplicas: 0
        maxReplicas: 1
        rules: [
          {
            name: 'http'
            http: {
              metadata: {
                concurrentRequests: '50'
              }
            }
          }
        ]
      }
    }
  }
}

output appUrl string = 'https://${app.properties.configuration.ingress.fqdn}'
output containerAppName string = app.name
output postgresHost string = postgres.properties.fullyQualifiedDomainName
