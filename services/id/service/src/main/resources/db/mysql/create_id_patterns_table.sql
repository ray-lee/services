/*	
 * create_id_patterns_table.sql
 *
 * Creates the "id_patterns" table, which stores ID patterns and their state.
 * Also sets the access permissions of that table.
 *
 * This document is a part of the source code and related artifacts
 * for CollectionSpace, an open source collections management system
 * for museums and related institutions:
 *
 * http://www.collectionspace.org
 * http://wiki.collectionspace.org
 *
 * Copyright © 2009 Regents of the University of California
 *
 * Licensed under the Educational Community License (ECL), Version 2.0.
 * You may not use this file except in compliance with this License.
 *
 * You may obtain a copy of the ECL 2.0 License at
 * https://source.collectionspace.org/collection-space/LICENSE.txt
 *
 * $LastChangedBy: aron $
 * $LastChangedRevision: 302 $
 * $LastChangedDate: 2009-07-15 17:42:23 -0700 (Wed, 15 Jul 2009) $
 */

CREATE DATABASE IF NOT EXISTS `cspace`;
USE `cspace`;

DROP TABLE IF EXISTS `id_patterns`;
CREATE TABLE `id_patterns` (
  `id_pattern_csid`        varchar(80) PRIMARY KEY,
  -- `id_pattern_uri`      varchar(200),
  `id_pattern_state`       varchar(8000),
  `last_generated_id`      varchar(255),
  `modified`               timestamp NOT NULL
                           default CURRENT_TIMESTAMP
                           on update CURRENT_TIMESTAMP,
  INDEX `id_pattern_csid_index` (`id_pattern_csid`)
  -- INDEX `id_pattern_uri_index` (`id_pattern_uri`)
) ENGINE=InnoDB;

GRANT SELECT, INSERT, UPDATE, DELETE
  on `id_patterns`
  to `test`;

SHOW WARNINGS;
