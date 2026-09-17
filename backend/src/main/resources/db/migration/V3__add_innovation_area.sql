ALTER TABLE dbo.projects
    ADD innovation_area VARCHAR(40) NULL;

UPDATE dbo.projects
SET innovation_area = 'OTHER'
WHERE innovation_area IS NULL;

ALTER TABLE dbo.projects
ALTER COLUMN innovation_area VARCHAR(40) NOT NULL;

ALTER TABLE dbo.projects
    ADD CONSTRAINT DF_projects_innovation_area
        DEFAULT 'OTHER' FOR innovation_area;
