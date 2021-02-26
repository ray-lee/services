-- A bug in checkbox handling in 4.3 created strange values for loanincourierinfo and
-- loanoutcourierinfo. Change these to normal values that the 6.1 checkbox input will understand.

UPDATE loansin_omca SET loanincourierinfo = 'true' WHERE loanincourierinfo = '["true"]';
UPDATE loansin_omca SET loanincourierinfo = 'false' WHERE loanincourierinfo = '[]';

UPDATE loansout_omca SET loanoutcourierinfo = 'true' WHERE loanoutcourierinfo = '["true"]';
UPDATE loansout_omca SET loanoutcourierinfo = 'false' WHERE loanoutcourierinfo = '[]';

-- Rename contactgroup (defined in omca acquisition extension) to acquisitioncontactgroup, to
-- resolve name collision with contactgroup that is defined in common organization record in 6.1.

ALTER TABLE contactgroup RENAME TO acquisitioncontactgroup;

ALTER TABLE acquisitioncontactgroup
  RENAME CONSTRAINT contactgroup_id_hierarchy_fk TO acquisitioncontactgroup_id_hierarchy_fk;

ALTER INDEX contactgroup_pk RENAME TO acquisitioncontactgroup_pk;

UPDATE hierarchy
  SET primarytype = 'acquisitionContactGroup'
  WHERE primarytype = 'contactGroup';

UPDATE hierarchy
  SET name = 'acquisitions_omca:acquisitionContactGroupList'
  WHERE name = 'acquisitions_omca:contactGroupList';

ANALYZE;
