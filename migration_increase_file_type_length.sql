-- Migration: Increase file_type column length to support long MIME types
-- Date: 2025-10-21
-- Description: Fix truncation error for Office document MIME types (Word, PowerPoint, Excel)

USE FileStorage;
GO

-- Increase file_type column length from VARCHAR(50) to VARCHAR(255)
ALTER TABLE files
ALTER COLUMN file_type VARCHAR(255);
GO

-- Verify the change
SELECT 
    COLUMN_NAME, 
    DATA_TYPE, 
    CHARACTER_MAXIMUM_LENGTH
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'files' AND COLUMN_NAME = 'file_type';
GO

PRINT 'Migration completed: file_type column length increased to 255';
