# AWS infrastructure

Production runs as two containers on AWS ECS behind CloudFront, in EU.
[ADR 0003](../docs/adr/0003-two-containers-on-ecs-behind-cloudfront.md) explains why, and
[`deployment.md`](../docs/implementation_details/deployment.md) explains how it fits together.

| File | What it is |
| :--- | :--- |
| `bootstrap.yaml` | Stack `3t-bootstrap`: the GitHub OIDC trust, the deploy role, and the CloudFormation execution role. Deployed once, by hand. |
| `app.yaml` | Stack `3t-run`: the VPC, RDS, ECS, the EC2 host or the ALB + Fargate (`ScaleMode`), and CloudFront. |
| `smoke-check.sh` | Checks the deployed app through CloudFront. `deploy.yml` runs it after every deploy. |
| `requirements.txt` | The pinned `cfn-lint` that CI runs on the templates. |

After the one-time setup below, every green CI run on `main` deploys by itself: `deploy.yml`
pushes both images to GHCR, updates `3t-run` to the new tags, and runs the smoke check.

## One-time setup

You need the AWS CLI v2, signed in with an admin profile, region `eu-central-1`
(`export AWS_REGION=eu-central-1`), and `gh` signed in to the repo.

1. **Check the plan.** In the console, open *Billing → Free plan* and confirm the account can use
   EC2, ECS, RDS and CloudFront. The free plan suspends the account when the credits run out, or
   after 6 months, unless you upgrade to the Paid plan.

2. **Create a budget.** In *Billing → Budgets*, create a monthly cost budget of **$40** with email
   alerts at 50, 80 and 100%. Do it from the console's *Explore AWS* activity, which also earns
   $20 of credits.

3. **Set up SES.**
   1. Verify the sender address. SES emails a link to click:
      ```sh
      aws sesv2 create-email-identity --email-identity <sender address>
      ```
   2. In *SES → SMTP settings*, create SMTP credentials. Note the user name; the password is
      shown once.
   3. While the account is in the SES sandbox, mail reaches verified addresses only, and the
      sender must be verified too. Verify each recipient the same way as the sender (their owner
      clicks the link). The sandbox allows 200 messages a day. Anyone else who registers gets
      no confirmation email.
   4. To leave the sandbox, verify a sending domain first (*SES → Identities → Create identity →
      Domain*, then add the DKIM CNAME records at your DNS provider). The console keeps
      *Request production access* disabled ("Domain verification needed") until a domain is
      verified. Then request access (free, about a day) and send from an address on that domain.

4. **Store the secrets** as SSM SecureStrings. The stack reads them by name.
   ```sh
   aws ssm put-parameter --name /3t/db-password --type SecureString \
     --value "$(openssl rand -base64 24 | tr -d '/+=')"
   aws ssm put-parameter --name /3t/mail-password --type SecureString --value '<SES SMTP password>'
   ```

5. **Make both GHCR packages public.** ECS pulls the images without credentials. On GitHub, open
   *Packages → 3t-backend → Package settings → Change visibility → Public*, then the same for
   `3t-frontend`. Check with `docker logout ghcr.io && docker manifest inspect
   ghcr.io/ovi-codes/3t-backend:main`.

6. **Deploy the bootstrap stack**, then read its outputs:
   ```sh
   aws cloudformation deploy --stack-name 3t-bootstrap --template-file infra/bootstrap.yaml \
     --capabilities CAPABILITY_IAM
   aws cloudformation describe-stacks --stack-name 3t-bootstrap --query 'Stacks[0].Outputs'
   ```

7. **Deploy the app stack** for the first time. This sets every parameter that `deploy.yml`
   does not pass. It takes about 20 minutes (RDS and CloudFront are the slow parts).
   ```sh
   ami=$(aws ssm get-parameter --query Parameter.Value --output text \
     --name /aws/service/ecs/optimized-ami/amazon-linux-2023/arm64/recommended/image_id)
   cf=$(aws ec2 describe-managed-prefix-lists --query 'PrefixLists[0].PrefixListId' --output text \
     --filters Name=prefix-list-name,Values=com.amazonaws.global.cloudfront.origin-facing)
   aws cloudformation deploy --stack-name 3t-run --template-file infra/app.yaml \
     --role-arn <ExecutionRoleArn> --capabilities CAPABILITY_IAM \
     --parameter-overrides EcsAmiId="$ami" CloudFrontPrefixListId="$cf" \
       MailFrom='<sender address>' MailUsername='<SES SMTP user name>' AdminEmails='<your account>'
   url=$(aws cloudformation describe-stacks --stack-name 3t-run --output text \
     --query "Stacks[0].Outputs[?OutputKey=='AppUrl'].OutputValue")
   bash infra/smoke-check.sh "$url"
   ```
   If RDS rejects the engine version, pick a current 16.x from
   `aws rds describe-db-engine-versions --engine postgres --engine-version 16` and set
   `EngineVersion` in `app.yaml`.

8. **Connect GitHub.** Limit the `production` environment to `main`, then set the variables that
   switch the deploy job on:
   ```sh
   gh api -X PUT repos/Ovi-codes/3T/environments/production \
     -F 'deployment_branch_policy[protected_branches]=false' \
     -F 'deployment_branch_policy[custom_branch_policies]=true'
   gh api -X POST repos/Ovi-codes/3T/environments/production/deployment-branch-policies -f name=main
   gh variable set AWS_REGION --body eu-central-1
   gh variable set AWS_DEPLOY_ROLE_ARN --body '<DeployRoleArn>'
   gh variable set AWS_CFN_EXEC_ROLE_ARN --body '<ExecutionRoleArn>'
   ```
   Then run *Actions → Deploy (AWS) → Run workflow* on `main` and check that the smoke check
   passes.

9. **Check by hand** on the `AppUrl`: register for an event and receive the email (to a verified
   address while in the sandbox), sign up, sign in, open the dashboard, and reload a deep link.
   In DevTools, `XSRF-TOKEN` and `JSESSIONID` must be `Secure`.

10. **Clean up** what Increment 14 left: the old `3t` GHCR package, and any `AZURE_*` secrets or
    variables (`gh secret list`, `gh variable list`).

## Operating

All commands assume the admin profile and `AWS_REGION=eu-central-1`. `<ExecutionRoleArn>` is the
`3t-bootstrap` output.

- **Logs:** `aws logs tail /3t-run/backend --follow` (or `/3t-run/frontend`). Kept 7 days.
- **Shell on the host** (`ScaleMode=single`), with no SSH port open:
  `aws ssm start-session --target <HostInstanceId output>`. Needs the Session Manager plugin.
- **Roll back:** revert the commit on `main`, and the deploy ships the revert. In a hurry,
  redeploy the earlier images by hand:
  ```sh
  aws cloudformation deploy --stack-name 3t-run --template-file infra/app.yaml \
    --role-arn <ExecutionRoleArn> --capabilities CAPABILITY_IAM \
    --parameter-overrides BackendImage=ghcr.io/ovi-codes/3t-backend:<sha> \
      FrontendImage=ghcr.io/ovi-codes/3t-frontend:<sha>
  ```
- **Change a parameter** (for example `AdminEmails`): the same command with that parameter in
  `--parameter-overrides`. Run it from an up-to-date `main`, because it also applies the template.
- **Switch `ScaleMode`:** pass `ScaleMode=fargate` (or `single`). It takes 10–15 minutes, and
  the app fails requests for a few of them, so do it in a quiet window. Then run
  `infra/smoke-check.sh`. `fargate` costs about $70 a month, so switch back when you are done.
- **Bump the host AMI:** pass a new `EcsAmiId` (step 7 shows the lookup). CloudFormation
  replaces the instance, and the Elastic IP moves to the new one. Expect a few minutes of
  downtime.
- **Rotate a secret:** `aws ssm put-parameter --overwrite` with the new value. For the database
  password, also run `aws rds modify-db-instance --db-instance-identifier <id>
  --master-user-password <new>`. Then restart the backend:
  `aws ecs update-service --cluster <ClusterName> --service <backend service> --force-new-deployment`.
- **Cost:** in Cost Explorer, `ScaleMode=single` costs about $1.25 a day.
- **Approve each deploy (optional):** in *Settings → Environments → production*, add yourself
  under *Required reviewers*. The `deploy` job then waits for your approval after every green
  push to `main`. The `images` job still runs without approval. Leave *Prevent self-review* off
  if you are the only maintainer. Private repos need a paid GitHub plan for this setting.
- **Pause deploys:** `gh variable delete AWS_DEPLOY_ROLE_ARN`. Pushes to `main` then build and
  push images but never change AWS, and this includes a manual *Run workflow*. Set the variable
  again to resume. A pause does not stop the running app. A stopped service can restart on the
  next deploy (the `single` services set `DesiredCount: 1`).
- **Tear down:** turn off the database's deletion protection
  (`aws rds modify-db-instance --db-instance-identifier <id> --no-deletion-protection`), then
  `aws cloudformation delete-stack --stack-name 3t-run`. RDS keeps a final snapshot, which costs
  storage until you delete it.
