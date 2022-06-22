# Upgrading MMI CollectionSpace from v4.5 to v7.0

Perform the following steps to upgrade MMI CollectionSpace from version 4.5 to version 7.0:

- [Install CollectionSpace 7.0](#install-collectionspace-70)
- [Build and deploy the MMI customizations](#build-and-deploy-mmi-customizations)
- [Migrate data](#migrate-data)
- [Verify the upgrade](#verify-the-upgrade)
- [Update permissions](#update-permissions)
- [Update reports and external integrations](#update-reports-and-external-integrations)
- [Install public gateway](#install-public-gateway)

## Install CollectionSpace 7.0

Install a standard distribution of CollectionSpace 7.0 on a server.

1. Run the [automated installer](https://collectionspace.atlassian.net/wiki/spaces/DOC/pages/2593660607/Automated+installer+for+CollectionSpace), or follow the [manual installation instructions](https://collectionspace.atlassian.net/wiki/spaces/DOC/pages/2593660645/Installing+on+Ubuntu+LTS).

   Note: The example commands shown in this document are written for an installation using the automated installer. Some URLs, usernames, directories, and commands may need to be adjusted for a manual installation.

1. Verify the installation.
   1. Log into the core tenant at `https://{hostname}/cspace/core/login`.
   1. Create a record. The record should save successfully.

## Build and deploy MMI customizations

Install the MMI customizations from the `mmi-7.0` branches.

1. Stop CollectionSpace.
   ```
   sudo systemctl stop collectionspace.service
   ```

1. As the CollectionSpace user, check out the `mmi-7.0` branches in the `services` and `application` source code directories.
   ```
   sudo su - collectionspace

   cd /opt/collectionspace/services
   git remote add ray-lee https://github.com/ray-lee/services.git
   git fetch ray-lee
   git checkout mmi-v7.0

   cd ../application
   git remote add ray-lee https://github.com/ray-lee/application.git
   git fetch ray-lee
   git checkout mmi-v7.0
   ```

1. Build and deploy the `services` and `application` projects from the `mmi-7.0` branches.
   ```
   cd ../services
   mvn clean install -DskipTests
   cd ../application
   mvn clean install -DskipTests
   cd ../services
   ant undeploy deploy
   ant create_db import -Drecreate_db=true

   exit
   ```

1. Set the MMI tenant to be accessible through the web server.
   1. Update `/etc/nginx/sites-enabled/cspace.conf`, replacing `/cspace/core` with `/cspace/mmi` where it appears.
      ```
      sudo sed -i 's|/cspace/core|/cspace/mmi|g' /etc/nginx/sites-enabled/cspace.conf
      ```

   1. Reload the web server configuration.
      ```
      sudo systemctl reload nginx.service
      ```

1. Start CollectionSpace.
   ```
   sudo systemctl start collectionspace.service
   ```

1. Verify the installation.
   1. Log into the mmi tenant at `https://{hostname}/cspace/mmi/login`.
   2. Create some records. They should save successfully.

1. Initialize the data directory where binary files are stored.
   1. Create a media record, and upload a file. This will create the data directory (`/opt/collectionspace/server/nuxeo-server/data/mmi`).

## Migrate data

Two types of data need to be migrated from 4.5 to 7.0: record data stored in postgres, and user-uploaded blobs (associated with media records), stored in the filesystem or S3.

### Migrate PostgreSQL databases

1. Stop CollectionSpace on both the 7.0 server and the 4.5 server.

   On 7.0:
   ```
   sudo systemctl stop collectionspace.service
   ```

   On 4.5:
   Run `bin/shutdown.sh` in the tomcat installation directory.

1. On the 4.5 server, export the `mmi_default` and `cspace_default` databases to a location writeable by the postgres user (e.g. /tmp):
   ```
   sudo -u postgres pg_dump -Fc -f /tmp/mmi_default.dump mmi_default
   sudo -u postgres pg_dump -Fc -f /tmp/cspace_default.dump cspace_default
   ```

1. Transfer the dump files to the 7.0 server, to a location readable by the postgres user (e.g. /tmp).

1. Run a script to create empty databases to receive 4.5 data. This will delete the existing `mmi_default` and `cspace_default` databases, which at this point only contain the test records just entered.
   ```
   cd /opt/collectionspace/services/upgrade_mmi_4.5_to_7.0
   sudo -u postgres psql -d postgres -f create_db.sql
   ```

1. Run a script to create missing text functions in the `nuxeo_default`, `mmi_default`, and `cspace_default` databases.
   ```
   sudo -u postgres psql -d nuxeo_default -f create_text_functions.sql
   sudo -u postgres psql -d mmi_default -f create_text_functions.sql
   sudo -u postgres psql -d cspace_default -f create_text_functions.sql
   ```

1. Restore the 4.5 databases into the newly created databases.
   1. Restore `mmi_default`.
      ```
      sudo -u postgres pg_restore -d mmi_default /tmp/mmi_default.dump
      ```

      Note: The following error may be safely ignored, if it appears:
      ```
      pg_restore: error: could not execute query: ERROR:  schema "public" already exists
      ```

   1. Restore `cspace_default`.
      ```
      sudo -u postgres pg_restore -d cspace_default /tmp/cspace_default.dump
      ```

      Note: The following error may be safely ignored, if it appears:
      ```
      pg_restore: error: could not execute query: ERROR:  schema "public" already exists
      ```

1. Upgrade the restored 4.5 databases to 7.0.
   1. Run a script to upgrade the `cspace_default` database.
      ```
      sudo -u postgres psql -d cspace_default -f upgrade_cspace.sql
      ```

      Note: Since 6.0, database upgrades are applied automatically when CollectionSpace is started. The above script only includes the changes that are needed to get from 4.5 to 6.0. Additional changes will be applied on startup.

1. Initialize permissions for record types that did not exist in 4.5.
   ```
   sudo su - collectionspace
   cd /opt/collectionspace/services
   ant import
   exit
   ```

1. The previous step can result in duplicate permissions, because of changes to how permissions are initialized in 7.0 vs. 4.5. Run a script to remove the duplicates.
   ```
   cd /opt/collectionspace/services/upgrade_mmi_4.5_to_7.0
   sudo -u postgres psql -d cspace_default -f dedupe_permissions.sql
   ```

1. Ensure time zone settings in Postgres, Java, and the OS are identical between 4.5 and 7.0. Otherwise, values in date fields may appear to change after the upgrade. The 7.0 automated installer sets all time zones to UTC, so if the 4.5 server was also using UTC, nothing needs to be done.

   1. To see the postgres time zone, use the command:
      ```
      sudo -u postgres psql -c "show timezone"
      ```

      To change the postgres time zone (for example, to US Pacific time):
      ```
      sudo -u postgres psql -c "alter system set timezone = 'America/Los_Angeles'"
      sudo systemctl reload postgresql
      ```

   1. To see the Java time zone setting on 4.5:
      ```
      cat $CSPACE_JEESERVER_HOME/bin/setenv.sh
      ```
      Look for the `JAVA_OPTS` variable, and find the setting for the `user.timezone` parameter. If no `user.timezone` is specified, Java is likely using the time zone of the OS.

      To change the Java time zone setting on 7.0:
      - As the `collectionspace` user, edit the file `/home/collectionspace/.config/environment.d/collectionspace.conf`. Find the `CATALINA_OPTS` variable, and change the `user.timezone` setting.

   1. To see the time zone setting in the OS:
      ```
      timedatectl
      ```
      Look for the Time zone setting.

      To change the time zone setting in the OS (for example, to US Pacific time):
      ```
      sudo timedatectl set-timezone America/Los_Angeles
      ```

### Migrate user-uploaded binaries

If user-uploaded files are stored in the local filesystem on the 4.5 server, perform the following steps to migrate the data to the 7.0 server. If the data was stored in S3, these steps are not needed; the 7.0 server can be configured to point to the same bucket. If the data was stored on network-attached storage, these steps are also not needed; the storage can be attached to the appropriate location on the 7.0 server.

1. Locate the data directory from 4.5. The directory resides in the tomcat server directory, at `nuxeo-server/data/mmi/data`.

1. Transfer the directory to the same location under tomcat on the 7.0 server. The existing `mmi/data` directory can be replaced, since it only contains the test file that was just uploaded.

1. Recursively set ownership of the data directory to the `collectionspace` user and group.

## Verify the upgrade

1. Start CollectionSpace.

1. Check that records migrated from 4.5 have correct data, and can be edited and saved.

1. In records migrated from 4.5, check that calendar date fields display the same values in 7.0. (If they do not, check the time zone settings, as described above.)

1. Check that media record thumbnails and snapshots appear, and that original content can be viewed.

1. Check that new records can be created and saved.

1. Check that permissions for roles migrated from 4.5 look correct.

1. Check that roles and users migrated from 4.5 can be saved.

1. Check that new roles and users can be created.

1. Check that permissions are enforced for users migrated from 4.5.

## Update permissions

New resources exist in 7.0 that did not exist in 4.5. Permissions on these resources will be set to `None` for any roles that were created in 4.5. For each role, review the access levels, and update them as needed. New resources include:

- **Data Update Invocations**

  Set to `Write` for roles that should be able to invoke data updates (batch jobs).

- **Exports**

  Set to `Write` for roles that should be able to export search results as CSV.

- **Report Invocations**

  Set to `Write` for roles that should be able to run reports.

- **Structured Date Parser**

  Set to `Read` for roles that should be able to invoke the structured date parser -- typically, any role that has `Write` permission to any record type.

## Update reports and external integrations

Since database schemas have changed between 4.5 and 7.0, reports and external integrations that retrieve CollectionSpace data through SQL queries or REST API calls may need to be updated. To see the changes, review the database update scripts:

- All scripts in [/src/main/resources/db/postgresql/upgrade](../src/main/resources/db/postgresql/upgrade)

## Install public gateway

The public gateway provides controlled public access to the CollectionSpace and Elasticsearch APIs, allowing anonymous users to retrieve published data.

1. In the CollectionSpace UI, use the Administration tab to create a CollectionSpace role and user to be used for anonymous access.

   1. Create a role with read-only access to a minimal set of resources. For a default installation of the CollectionSpace public browser, `Read` permission is required on Objects, Media Handling, and Blobs. Set the permission to `None` for all other resources.

   1. Create a user, assigning the newly created role. Make note of the email address and password assigned to this user.

   1. Verify that the user can access the CollectionSpace REST API (replace `gateway@movingimage.us` with the email address of your newly created user):
      ```
      curl -i -u gateway@movingimage.us http://localhost:8180/cspace-services/systeminfo
      ```

1. Enable Elasticsearch.

   Note: The CollectionSpace automated installer automatically installs and starts Elasticsearch. If you did not use the automated installer to install CollectionSpace, you must install Elasticsearch manually. Ensure that the Elasticsearch service is running, and is accessible on port 9200 before continuing.

   1. As the CollectionSpace user, edit `/opt/collectionspace/server/nuxeo-server/config/nuxeo.properties`, and set `elasticsearch.enabled` to `true`.
      ```
      sudo -u collectionspace sed -i 's|^elasticsearch.enabled=false|elasticsearch.enabled=true|' /opt/collectionspace/server/nuxeo-server/config/nuxeo.properties
      ```

   1. Restart CollectionSpace.
      ```
      sudo systemctl restart collectionspace.service
      ```

1. Create the Elasticsearch index.

   1. Reindex CollectionSpace data.
      ```
      curl -i -u admin@movingimage.us -X POST http://localhost:8180/cspace-services/index/elasticsearch
      ```

   1. Verify that the index exists:
      ```
      curl -i http://localhost:9200/mmi_default/_count
      ```

1. As the CollectionSpace user, clone the [cspace-public-gateway](https://github.com/collectionspace/cspace-public-gateway) repository.
   ```
   sudo su - collectionspace

   cd /opt/collectionspace
   git clone https://github.com/collectionspace/cspace-public-gateway
   ```

1. Build the application war.
   ```
   cd cspace-public-gateway
   mvn clean package
   ```

1. Deploy the application to the CollectionSpace Tomcat container.
   ```
   cp target/org.collectionspace.publicbrowser-1.0.0-SNAPSHOT.war /opt/collectionspace/server/webapps/gateway.war
   ```

1. Configure the gateway.

   1. Add routes.

      - If CollectionSpace was installed using the automated installer, edit `/home/collectionspace/.config/environment.d/collectionspace.conf`, and add the `SPRING_APPLICATION_JSON` environment variable. Replace the `username` and `password` values with the email address and password of the anonymous access user created above.
        ```
        SPRING_APPLICATION_JSON='
        {
          "zuul": {
            "routes": {
              "mmi-cspace-services": {
                "password": "gatewaypassword",
                "path": "/mmi/cspace-services/**",
                "url": "http://localhost:8180/cspace-services",
                "username": "gateway@movingimage.us"
              },
              "mmi-es": {
                "path": "/mmi/es/**",
                "url": "http://localhost:9200/mmi_default"
              }
            }
          }
        }
        '
        ```

      - If CollectionSpace was installed manually, the `SPRING_APPLICATION_JSON` environment variable may be added to `/opt/collectionspace/server/bin/setenv.sh`. Replace the `username` and `password` values with the email address and password of the anonymous access user created above.
        ```
        export SPRING_APPLICATION_JSON='
        {
          "zuul": {
            "routes": {
              "mmi-cspace-services": {
                "password": "gatewaypassword",
                "path": "/mmi/cspace-services/**",
                "url": "http://localhost:8180/cspace-services",
                "username": "gateway@movingimage.us"
              },
              "mmi-es": {
                "path": "/mmi/es/**",
                "url": "http://localhost:9200/mmi_default"
              }
            }
          }
        }
        '
        ```

   1. Restart CollectionSpace.
      ```
      exit
      sudo systemctl restart collectionspace.service
      ```

1. Set the gateway to be accessible through the web server.

   1. As root, edit `/etc/nginx/sites-enabled/cspace.conf`, and add a mapping for `/gateway/mmi`:
      ```
      location /gateway/mmi/ {
          proxy_pass http://collectionspace;
      }
      ```

   1. Reload the web server configuration.
      ```
      sudo systemctl reload nginx.service
      ```

1. Verify the installation.

   1. Verify anonymous access to the CollectionSpace REST API (replace `{hostname}` with the hostname of your CollectionSpace server):
      ```
      curl https://{hostname}/gateway/mmi/cspace-services/systeminfo
      ```

   1. Verify anonymous access to the Elasticsearch API (replace `{hostname}` with the hostname of your CollectionSpace server):
      ```
      curl https://{hostname}/gateway/mmi/es/_count
      ```
