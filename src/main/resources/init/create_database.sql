-- Create the database if it doesn't exist
DO
$$
BEGIN
   IF NOT EXISTS (
      SELECT FROM pg_database WHERE datname = 'notifications'
   ) THEN
      PERFORM dblink_exec('dbname=postgres user=postgres password=1234', 'CREATE DATABASE notifications');
   END IF;
END
$$;
