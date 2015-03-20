--
-- Tables
--

-- nebula_entries
create table nebula_entries
(
  id       INTEGER not null primary key autoincrement,
  uuid     TEXT not null,
  target   TEXT not null,
  metadata INTEGER not null,
  proxy    INTEGER not null,
  parent   TEXT,
  children TEXT,
  constraint UK_NEBULA_ENTRIES_1 unique (uuid),
  foreign key (metadata) references nebula_metadata (id) on update CASCADE on delete CASCADE
);

-- nebula_metadata
create table nebula_metadata
(
  id      INTEGER not null primary key autoincrement,
  created INTEGER not null
);

