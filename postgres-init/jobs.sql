CREATE DATABASE job_finder_db;

-- Connect to the job_finder_db database to build tables
\c job_finder_db;

-- Enable required extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Define custom ENUM for job lifecycle management
DO $$ BEGIN
    CREATE TYPE job_status AS ENUM ('ACTIVE', 'STALE', 'EXPIRED', 'ARCHIVED');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 1. Companies Table
DROP TABLE IF EXISTS jobs CASCADE;
DROP TABLE IF EXISTS companies CASCADE;

CREATE TABLE companies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL UNIQUE,
    ats_type VARCHAR(50) NOT NULL,            
    board_token VARCHAR(255) NOT NULL,         
    website_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Jobs Table
CREATE TABLE jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    
    -- Role & Compensation
    experience_level VARCHAR(50),              -- e.g., 'Junior', 'Mid', 'Senior', 'Lead'
    employment_type VARCHAR(50),               -- e.g., 'Full-time', 'Contract', 'Internship'
    salary_min INTEGER,                        -- e.g., 90000
    salary_max INTEGER,                        -- e.g., 130000
    salary_currency VARCHAR(10) DEFAULT 'USD', -- e.g., 'USD', 'EUR', 'AED'
    
    location VARCHAR(255),
    department VARCHAR(255),
    description_text TEXT,
    apply_url TEXT NOT NULL,
    ats_job_id VARCHAR(100),                   
    
    -- Idempotency & Deduplication Key
    fingerprint_hash VARCHAR(64) UNIQUE NOT NULL, 
    
    -- Lifecycle State
    status job_status DEFAULT 'ACTIVE',
    
    -- Timestamps
    posted_at TIMESTAMP WITH TIME ZONE,
    last_seen_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- PostgreSQL Full-Text Search Vector
    search_vector tsvector
);

-- 3. Indexes for Sub-Millisecond Search Performance
CREATE INDEX idx_jobs_search_vector ON jobs USING GIN (search_vector);
CREATE INDEX idx_jobs_active_posted ON jobs(status, posted_at DESC);
CREATE INDEX idx_jobs_company_id ON jobs(company_id);

-- 4. Automated Search Vector Trigger
CREATE OR REPLACE FUNCTION update_job_search_vector() RETURNS trigger AS $$
BEGIN
  NEW.search_vector :=
    setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(NEW.experience_level, '')), 'A') || 
    setweight(to_tsvector('english', coalesce(NEW.department, '')), 'B') ||
    setweight(to_tsvector('english', coalesce(NEW.location, '')), 'B') ||
    setweight(to_tsvector('english', coalesce(NEW.description_text, '')), 'C');
  RETURN NEW;
END
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_jobs_search_vector_update ON jobs;

CREATE TRIGGER trg_jobs_search_vector_update
BEFORE INSERT OR UPDATE ON jobs
FOR EACH ROW EXECUTE FUNCTION update_job_search_vector();