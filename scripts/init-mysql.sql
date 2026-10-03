CREATE DATABASE IF NOT EXISTS commerce_cs
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'commerce'@'localhost' IDENTIFIED BY 'commerce_cs_dev';
CREATE USER IF NOT EXISTS 'commerce'@'127.0.0.1' IDENTIFIED BY 'commerce_cs_dev';

GRANT ALL PRIVILEGES ON commerce_cs.* TO 'commerce'@'localhost';
GRANT ALL PRIVILEGES ON commerce_cs.* TO 'commerce'@'127.0.0.1';
FLUSH PRIVILEGES;
