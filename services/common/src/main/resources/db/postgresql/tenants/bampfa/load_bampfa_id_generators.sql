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
 * load_ucjeps_id_generators.sql
 *
 * Loads ucjeps-specific data into the "id_generators" table,
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


-- BAMPFA_CONDITION_CHECK 
INSERT INTO id_generators
    (csid, displayname, description, priority, last_generated_id, id_generator_state)
  SELECT
     '63a779aa-7d25-4af2-a2d9-66ce72fc70aa',
     'BAMPFA Condition Check Number',
     'Identifies a condition check record.',
     '9',
     '',
'<org.collectionspace.services.id.SettableIDGenerator>
  <parts>
    <org.collectionspace.services.id.StringIDGeneratorPart>
      <initialValue>CC</initialValue>
      <currentValue>CC</currentValue>
    </org.collectionspace.services.id.StringIDGeneratorPart>
    <org.collectionspace.services.id.StringIDGeneratorPart>
      <initialValue>.</initialValue>
      <currentValue>.</currentValue>
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
  WHERE '63a779aa-7d25-4af2-a2d9-66ce72fc70aa' NOT IN
        (
        SELECT  csid
        FROM    id_generators
        );


-- BAMPFA LOANS_OUT_NUMBER
INSERT INTO id_generators
    (csid, displayname, description, priority, last_generated_id, id_generator_state)
  SELECT
     '20977f63-0a27-457f-ad66-7cffc13b0d77',
     'BAMPFA Loan Out Number',
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
    <org.collectionspace.services.id.StringIDGeneratorPart>
      <initialValue>.</initialValue>
      <currentValue>.</currentValue>
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
  WHERE '20977f63-0a27-457f-ad66-7cffc13b0d77' NOT IN
        (
        SELECT  csid
        FROM    id_generators
        );

-- BAMPFA_STUDY_NUMBER
INSERT INTO id_generators
    (csid, displayname, description, priority, last_generated_id, id_generator_state)
  SELECT
     '5714b6d1-f4a0-4f6e-a61f-768f2eb6ab1e',
     'BAMPFA Study Number',
     'Identifies study-related intake activities,
in which a lot of one or more collection objects is
formally acquired for study.',
     '9',
     '',
'<org.collectionspace.services.id.SettableIDGenerator>
   <parts>
    <org.collectionspace.services.id.StringIDGeneratorPart>
      <initialValue>ST</initialValue>
      <currentValue>ST</currentValue>
    </org.collectionspace.services.id.StringIDGeneratorPart>
    <org.collectionspace.services.id.StringIDGeneratorPart>
      <initialValue>.</initialValue>
      <currentValue>.</currentValue>
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
  WHERE '5714b6d1-f4a0-4f6e-a61f-768f2eb6ab1e' NOT IN
        (
        SELECT  csid
        FROM    id_generators
        );

-- BAMPFA_TEMPORARY_RECEIPT
INSERT INTO id_generators
    (csid, displayname, description, priority, last_generated_id, id_generator_state)
  SELECT
     '4842b988-cf07-4e47-b262-282f4ededb90',
     'Tempory receipt',
     '<DESCRIPTION>',
     '9',
     '',
'<org.collectionspace.services.id.SettableIDGenerator>
  <parts>
    <org.collectionspace.services.id.StringIDGeneratorPart>
      <initialValue>TR</initialValue>
      <currentValue>TR</currentValue>
    </org.collectionspace.services.id.StringIDGeneratorPart>
    <org.collectionspace.services.id.StringIDGeneratorPart>
      <initialValue>.</initialValue>
      <currentValue>.</currentValue>
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
  WHERE '4842b988-cf07-4e47-b262-282f4ededb90' NOT IN
        (
        SELECT  csid
        FROM    id_generators
        );
