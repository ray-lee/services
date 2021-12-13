/*
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
 */

/*
 * load_mmi_id_generators.sql
 *
 * Loads mmi-specific data into the "id_generators" table,
 * used by the ID Service.
 *
 * $LastChangedRevision$
 * $LastChangedDate$
 */

/*
 * Note: in the priority column, values range from  '1' (highest)
 * to '9' (lowest).
 */

/*
 * NOTE: In the id_generator_state column, for numeric sequence parts
 * whose first generated value should start at the initial value
 * (such as '1'), enter '-1' for the <currentValue>
 *
 * Otherwise, the first generated value will be the next value
 * in the sequence after the initial value (e.g. '2', if the
 * initial value is '1').
 */

-- INTAKE_NUMBER
-- status: new
-- format: PA2019.1

INSERT INTO id_generators
    (csid, displayname, description, priority, last_generated_id, id_generator_state)
  SELECT
     '57602930-0b17-11e9-b353-5b98fea7f455',
     'Intake Number',
     'Identifies intake activities, in which a lot of one
or more collection objects enters the institution.',
     '9',
     '',
'<org.collectionspace.services.id.SettableIDGenerator>
   <parts>
    <org.collectionspace.services.id.StringIDGeneratorPart>
      <initialValue>PA</initialValue>
      <currentValue>PA</currentValue>
    </org.collectionspace.services.id.StringIDGeneratorPart>
    <org.collectionspace.services.id.YearIDGeneratorPart>
      <currentValue></currentValue>
    </org.collectionspace.services.id.YearIDGeneratorPart>
    <org.collectionspace.services.id.StringIDGeneratorPart>
      <initialValue>.</initialValue>
      <currentValue>.</currentValue>
    </org.collectionspace.services.id.StringIDGeneratorPart>
    <org.collectionspace.services.id.NumericIDGeneratorPart>
      <maxLength>6</maxLength>
      <initialValue>1</initialValue>
      <currentValue>-1</currentValue>
    </org.collectionspace.services.id.NumericIDGeneratorPart>
  </parts>
</org.collectionspace.services.id.SettableIDGenerator>'
  WHERE '57602930-0b17-11e9-b353-5b98fea7f455' NOT IN
        (
        SELECT  csid
        FROM    id_generators
        );

 -- LOANS_IN_OBJECT_NUMBER
 -- status: new
 -- format: IL2019.1.1

INSERT INTO id_generators
    (csid, displayname, description, priority, last_generated_id, id_generator_state)
  SELECT
     'd93c53ae-0b0b-11e9-b59a-172b63ba4833',
     'Loan In Object Number',
     'Identifies activities in which collection objects are
received on loan.',
     '9',
     '',
'<org.collectionspace.services.id.SettableIDGenerator>
  <parts>
    <org.collectionspace.services.id.StringIDGeneratorPart>
      <initialValue>IL</initialValue>
      <currentValue>IL</currentValue>
    </org.collectionspace.services.id.StringIDGeneratorPart>
    <org.collectionspace.services.id.YearIDGeneratorPart>
      <currentValue></currentValue>
    </org.collectionspace.services.id.YearIDGeneratorPart>
    <org.collectionspace.services.id.StringIDGeneratorPart>
      <initialValue>.</initialValue>
      <currentValue>.</currentValue>
    </org.collectionspace.services.id.StringIDGeneratorPart>
    <org.collectionspace.services.id.NumericIDGeneratorPart>
      <maxLength>6</maxLength>
      <initialValue>1</initialValue>
      <currentValue>-1</currentValue>
    </org.collectionspace.services.id.NumericIDGeneratorPart>
    <org.collectionspace.services.id.StringIDGeneratorPart>
      <initialValue>.</initialValue>
      <currentValue>.</currentValue>
    </org.collectionspace.services.id.StringIDGeneratorPart>
    <org.collectionspace.services.id.NumericIDGeneratorPart>
      <maxLength>6</maxLength>
      <initialValue>1</initialValue>
      <currentValue>-1</currentValue>
    </org.collectionspace.services.id.NumericIDGeneratorPart>
  </parts>
</org.collectionspace.services.id.SettableIDGenerator>'
  WHERE 'd93c53ae-0b0b-11e9-b59a-172b63ba4833' NOT IN
        (
        SELECT  csid
        FROM    id_generators
        );

-- LOANS_IN_LOT_NUMBER
-- status: new
-- format: IL2019.1

INSERT INTO id_generators
    (csid, displayname, description, priority, last_generated_id, id_generator_state)
  SELECT
     '45638dca-0b0e-11e9-ba06-f371ed1a3771',
     'Loan In Lot Number',
     'Identifies activities in which collection objects are
received on loan.',
     '9',
     '',
'<org.collectionspace.services.id.SettableIDGenerator>
  <parts>
    <org.collectionspace.services.id.StringIDGeneratorPart>
      <initialValue>IL</initialValue>
      <currentValue>IL</currentValue>
    </org.collectionspace.services.id.StringIDGeneratorPart>
    <org.collectionspace.services.id.YearIDGeneratorPart>
      <currentValue></currentValue>
    </org.collectionspace.services.id.YearIDGeneratorPart>
    <org.collectionspace.services.id.StringIDGeneratorPart>
      <initialValue>.</initialValue>
      <currentValue>.</currentValue>
    </org.collectionspace.services.id.StringIDGeneratorPart>
    <org.collectionspace.services.id.NumericIDGeneratorPart>
      <maxLength>6</maxLength>
      <initialValue>1</initialValue>
      <currentValue>-1</currentValue>
    </org.collectionspace.services.id.NumericIDGeneratorPart>
  </parts>
</org.collectionspace.services.id.SettableIDGenerator>'
  WHERE '45638dca-0b0e-11e9-ba06-f371ed1a3771' NOT IN
        (
        SELECT  csid
        FROM    id_generators
        );

-- LOANS_OUT_LOT_NUMBER
-- status: new
-- format: OL2019.1

INSERT INTO id_generators
    (csid, displayname, description, priority, last_generated_id, id_generator_state)
  SELECT
     '69eabcea-0b0e-11e9-bd52-135d0ac3b36f',
     'Loan Out Lot Number',
     'Identifies activities in which collection objects are
loaned out of the institution.',
     '9',
     '',
'<org.collectionspace.services.id.SettableIDGenerator>
  <parts>
    <org.collectionspace.services.id.StringIDGeneratorPart>
      <initialValue>OL</initialValue>
      <currentValue>OL</currentValue>
    </org.collectionspace.services.id.StringIDGeneratorPart>
    <org.collectionspace.services.id.YearIDGeneratorPart>
      <currentValue></currentValue>
    </org.collectionspace.services.id.YearIDGeneratorPart>
    <org.collectionspace.services.id.StringIDGeneratorPart>
      <initialValue>.</initialValue>
      <currentValue>.</currentValue>
    </org.collectionspace.services.id.StringIDGeneratorPart>
    <org.collectionspace.services.id.NumericIDGeneratorPart>
      <maxLength>6</maxLength>
      <initialValue>1</initialValue>
      <currentValue>-1</currentValue>
    </org.collectionspace.services.id.NumericIDGeneratorPart>
  </parts>
</org.collectionspace.services.id.SettableIDGenerator>'
  WHERE '69eabcea-0b0e-11e9-bd52-135d0ac3b36f' NOT IN
        (
        SELECT  csid
        FROM    id_generators
        );
