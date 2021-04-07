# Upgrading CollectionSpace from v4.3 to v6.1

Perform the following steps to upgrade CollectionSpace from version 4.3 to version 6.1:

- [Install CollectionSpace 6.1](#install-collectionspace-61)
- [Build and deploy the OMCA customizations](#build-and-deploy-omca-customizations)
- [Migrate data](#migrate-data)
- [Verify the upgrade](#verify-the-upgrade)
- [Update permissions](#update-permissions)
- [Update reports and external integrations](#update-reports-and-external-integrations)

## Install CollectionSpace 6.1

Install a standard distribution of CollectionSpace 6.1 on a server.

1. Run the [automated installer](https://collectionspace.atlassian.net/wiki/spaces/DOC/pages/1545306640/Automated+installer+for+CollectionSpace), or follow the [manual installation instructions](https://collectionspace.atlassian.net/wiki/spaces/DOC/pages/1545306677/Installing+on+Ubuntu+LTS).

   Note: The automated installer does not yet work on Ubuntu 20.04 LTS, but works on 18.04 LTS. The manual installation instructions work on both. The example commands shown in this document are written for an installation using the automated installer. Some URLs, usernames, directories, and commands may need to be adjusted for a manual installation.

1. Verify the installation.
   1. Log into the core tenant at `https://{hostname}/cspace/core/login`.
   1. Create a record. The record should save successfully.

## Build and deploy OMCA customizations

Install the OMCA customizations from the `omca-6.1` branches.

1. Stop CollectionSpace.
   ```
   sudo systemctl stop collectionspace.service
   ```

1. Change the instance ID from `_default` to `_omca`, to match the instance ID from the 4.3 installation.
   ```
   sudo sed -i 's/"_default"/"_omca"/' /home/collectionspace/.config/environment.d/collectionspace.conf
   sudo rm -r /opt/collectionspace/server/cspace/services/db/postgresql
   ```

1. As the CollectionSpace user, check out the `omca-6.1` branches in the `services` and `application` source code directories.
   ```
   sudo su - collectionspace

   cd /opt/collectionspace/services
   git remote add ray-lee https://github.com/ray-lee/services.git
   git fetch ray-lee
   git checkout omca-6.1

   cd ../application
   git remote add ray-lee https://github.com/ray-lee/application.git
   git fetch ray-lee
   git checkout omca-6.1
   ```

1. Build and deploy the `services` and `application` projects from the `omca-6.1` branches.
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

1. Set the OMCA tenant to be accessible through the web server.
   1. Update `/etc/nginx/sites-enabled/cspace.conf`, replacing `/cspace/core` with `/cspace/omca` where it appears.
      ```
      sudo sed -i 's|/cspace/core|/cspace/omca|g' /etc/nginx/sites-enabled/cspace.conf
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
   1. Log into the omca tenant at `https://{hostname}/cspace/omca/login`.
   2. Create some records. They should save successfully.

1. Initialize the data directory where binary files are stored.
   - Create a media or restricted media record, and upload a file. This will create the data directory (`/opt/collectionspace/server/nuxeo-server/data/omca_domain`).

## Migrate data

Two types of data need to be migrated from 4.3 to 6.1: record data stored in postgres, and user-uploaded blobs (associated with media records), stored in the filesystem or S3.

### Migrate PostgreSQL databases

1. Stop CollectionSpace on both the 6.1 server and the 4.3 server.
   ```
   sudo systemctl stop collectionspace.service
   ```

1. On the 4.3 server, export the `omca_domain_omca` and `cspace_omca` databases:
   ```
   sudo -u postgres pg_dump -Fc -f omca_domain_omca.dump omca_domain_omca
   sudo -u postgres pg_dump -Fc -f cspace_omca.dump cspace_omca
   ```

1. Transfer the dump files to the 6.1 server, to a location accessible by the postgres user.

1. Run a script to create empty databases to receive 4.3 data. This will delete the existing `omca_domain_omca` and `cspace_omca` databases, which at this point only contain the test records just entered.
   ```
   cd /opt/collectionspace/services/upgrade_4.3_to_6.1
   sudo -u postgres psql -d postgres -f create_db.sql
   ```

1. Restore the 4.3 databases into the newly created databases.
   1. Restore `omca_domain_omca`.
      ```
      sudo -u postgres pg_restore -e -d omca_domain_omca omca_domain_omca.dump
      ```

   1. Restore `cspace_omca`.
      ```
      sudo -u postgres pg_restore -e -d cspace_omca cspace_omca.dump
      ```

1. Upgrade the restored 4.3 databases to 6.1.
   1. Run a script to upgrade the `omca_domain_omca` database.
      ```
      cd /opt/collectionspace/services/upgrade_4.3_to_6.1
      sudo -u postgres psql -d omca_domain_omca -f upgrade_nuxeo.sql
      ```

      Note: Since 6.0, database upgrades are applied automatically when CollectionSpace is started. The above script only includes the changes that are needed to get from 4.3 to 6.0. Additional changes will be applied on startup.

   1. Run a script to upgrade the `cspace_omca` database.
      ```
      sudo -u postgres psql -d cspace_omca -f upgrade_cspace.sql
      ```

1. Initialize permissions for record types that did not exist in 4.3.
   ```
   sudo su - collectionspace
   cd /opt/collectionspace/services
   ant import
   exit
   ```

1. The previous step can result in duplicate permissions, because of changes to how permissions are initialized in 6.1 vs. 4.3. Run a script to remove the duplicates.
   ```
   cd /opt/collectionspace/services/upgrade_4.3_to_6.1
   sudo -u postgres psql -d cspace_omca -f dedupe_permissions.sql
   ```

1. Delete `_default` databases and roles that were created from the initial installation. These aren't needed, since we're now using the `_omca` instance ID.
   ```
   sudo -u postgres psql -f drop_default.sql
   ```

### Migrate user-uploaded binaries

If user-uploaded files are stored in the local filesystem on the 4.3 server, perform the following steps to migrate the data to the 6.1 server. If the data was stored in S3, these steps are not needed; the 6.1 server can be configured to point to the same bucket. If the data was stored on network-attached storage, these steps are also not needed; the storage can be attached to the appropriate location on the 6.1 server.

1. Locate the data directory from 4.3. The directory resides in the tomcat server directory, at `nuxeo-server/data/omca_domain/data`.

1. Transfer the directory to the same location under tomcat on the 6.1 server. The existing `omca_domain/data` directory can be replaced, since it only contains the test file that was just uploaded.

1. If necessary, recursively set ownership of the data directory to the user that runs CollectionSpace.

## Verify the upgrade

1. Start CollectionSpace.

1. Check that records migrated from 4.3 have correct data, and can be edited and saved.

1. Check that media record thumbnails and snapshots appear, and that original content can be viewed.

1. Repeat step 3 for restricted media.

1. Check that new records can be created and saved.

1. Check that permissions for roles migrated from 4.3 look correct.

1. Check that roles and users migrated from 4.3 can be saved.

1. Check that new roles and users can be created.

1. Check that permissions are enforced for users migrated from 4.3.

## Update permissions

New resources exist in 6.1 that did not exist in 4.3. Permissions on these resources will be set to `None` for any roles that were created in 4.3. For each role, review the access levels, and update them as needed. New resources include:

- **Data Update Invocations**

  Set to `Write` for roles that should be able to invoke data updates (batch jobs).

- **Exports**

  Set to `Write` for roles that should be able to export search results as CSV.

- **Report Invocations**

  Set to `Write` for roles that should be able to run reports.

- **Structured Date Parser**

  Set to `Read` for roles that should be able to invoke the structured date parser -- typically, any role that has `Write` permission to any record type.

## Update reports and external integrations

Since database schemas have changed between 4.3 and 6.1, reports and external integrations that retrieve CollectionSpace data through SQL queries or REST API calls may need to be updated. To see the changes, review the database update scripts:

- [upgrade_nuxeo.sql](upgrade_nuxeo.sql)
- All scripts in [/src/main/resources/db/postgresql/upgrade](../src/main/resources/db/postgresql/upgrade)
