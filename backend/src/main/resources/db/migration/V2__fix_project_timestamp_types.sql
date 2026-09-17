DECLARE @constraintName SYSNAME;
DECLARE @sql NVARCHAR(MAX);

-- Remove DEFAULT de created_at
SELECT @constraintName = dc.name
FROM sys.default_constraints dc
         INNER JOIN sys.columns c
                    ON c.default_object_id = dc.object_id
         INNER JOIN sys.tables t
                    ON t.object_id = c.object_id
WHERE t.name = 'projects'
  AND SCHEMA_NAME(t.schema_id) = 'dbo'
  AND c.name = 'created_at';

IF @constraintName IS NOT NULL
BEGIN
    SET @sql = N'ALTER TABLE dbo.projects DROP CONSTRAINT '
        + QUOTENAME(@constraintName);

EXEC sys.sp_executesql @sql;
END;

-- Remove DEFAULT de updated_at
SET @constraintName = NULL;

SELECT @constraintName = dc.name
FROM sys.default_constraints dc
         INNER JOIN sys.columns c
                    ON c.default_object_id = dc.object_id
         INNER JOIN sys.tables t
                    ON t.object_id = c.object_id
WHERE t.name = 'projects'
  AND SCHEMA_NAME(t.schema_id) = 'dbo'
  AND c.name = 'updated_at';

IF @constraintName IS NOT NULL
BEGIN
    SET @sql = N'ALTER TABLE dbo.projects DROP CONSTRAINT '
        + QUOTENAME(@constraintName);

EXEC sys.sp_executesql @sql;
END;

-- Alinha SQL Server ao java.time.Instant
ALTER TABLE dbo.projects
ALTER COLUMN created_at DATETIMEOFFSET(6) NOT NULL;

ALTER TABLE dbo.projects
ALTER COLUMN updated_at DATETIMEOFFSET(6) NOT NULL;

-- Recria DEFAULT constraints com nomes determinísticos e UTC
ALTER TABLE dbo.projects
    ADD CONSTRAINT DF_projects_created_at
        DEFAULT TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')
    FOR created_at;

ALTER TABLE dbo.projects
    ADD CONSTRAINT DF_projects_updated_at
        DEFAULT TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')
    FOR updated_at;