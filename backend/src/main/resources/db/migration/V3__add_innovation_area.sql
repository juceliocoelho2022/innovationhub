ALTER TABLE dbo.projects
    ADD innovation_area VARCHAR(40) NULL;

-- SQL Server compiles a batch before executing it. Statements that reference a
-- column added earlier in the same batch can therefore fail with
-- "Invalid column name". Execute dependent statements dynamically so each one
-- is compiled only after the column exists.
EXEC sys.sp_executesql N'
    UPDATE dbo.projects
    SET innovation_area = ''OTHER''
    WHERE innovation_area IS NULL;
';

EXEC sys.sp_executesql N'
    ALTER TABLE dbo.projects
    ALTER COLUMN innovation_area VARCHAR(40) NOT NULL;
';

EXEC sys.sp_executesql N'
    ALTER TABLE dbo.projects
        ADD CONSTRAINT DF_projects_innovation_area
        DEFAULT ''OTHER'' FOR innovation_area;
';
