-- Type used by utils.getnagpra(cocid varchar) to contain PAHMA NAGPRA Repatriation Compliance data
-- for the pahmapahmaRepatriationNAGPRAdenorm.jrxml report.
-- Must be created before function utils.getnagpra(cocid varchar).

CREATE TYPE nagpratype AS (
    cocsid varchar,
    coid varchar,
    pos integer,
    objectNumber varchar,
    nagpraInventoryName varchar,
    nagpraCategory varchar,
    graveAssocCode varchar,
    repatriationNote varchar,
    nagpraCulturalDetermination varchar,
    nagpraDetermCulture varchar,
    nagpraDetermType varchar,
    nagpraDetermBy varchar,
    nagpraDetermNote varchar,
    nagpraReportFiled boolean,
    nagpraReportFiledWith varchar,
    nagpraReportFiledBy varchar,
    nagpraReportFiledDate varchar,
    nagpraReportFiledNote varchar,
    reference varchar,
    referenceNote varchar
);
