(ns nebula.store.entry
  (:require [schema.core       :as schema]
            [nebula.store.blob :as blob]))

;;; Metadata contains any additional information that should be
;;; presented with an Entry. This currently consists of the time the
;;; Entry was created.
(schema/defrecord Metadata
    [created :- long])

;;; An Entry associates some notion of a blob with additional data.
(schema/defrecord Entry
    [id       :- schema/Str
     target   :- schema/Str
     proxy    :- schema/Bool
     metadata :- Metadata
     parent   :- (schema/maybe schema/Str)
     children :- [schema/Str]])

(defn current-time
  "current-time returns the system time as a Unix timestamp."
  []
  (quot (System/currentTimeMillis) 1000))

;;; sMetadata defines a schema for Metadata; its only purpose is to
;;; allow users of this library to avoid having to additionally import
;;; the Metadata class.
(def sMetadata Metadata)

(schema/defn now :- sMetadata
  "now returns a new Metadata entry with the current time."
  []
  (Metadata. (current-time)))

;;; sEntry defines a schema for Entry; its only purpose is to allow
;;; users of this library to avoid having to additionally import the
;;; Entry class.
(def sEntry Entry)

(schema/defn gen-uuid :- schema/Str
  "gen-uuid returns a new random UUID."
  []
  (str
   (java.util.UUID/randomUUID)))

(schema/defn uuid? :- schema/Bool
  "Returns true if the string is a UUID."
  [s :- schema/Str]
  (if (string? s)
    (seq
     (re-seq
      #"^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$"
      s))
    false))

(defn create-entry
  "create-entry creates a new entry from a blob identifier. If from is
  non-nil, it should contain the entry identifier for the parent."
  [blob-id from]
  (when (blob/valid-sha256? blob-id)
    (Entry.
     (gen-uuid)
     blob-id
     false
     (now)
     (when (or (blob/valid-sha256? from) (uuid? from)) from)
     nil)))

(schema/defn ^:always-validate 
  proxy-entry :- Entry
  "proxy-entry creates a new proxied entry from another entry. This
  does not support proxying the tree; the caller should handler
  walking a tree of proxied entries and setting the parent and
  children appropriately."
  [e :- Entry]
  (Entry.
   (gen-uuid)
   (:id e)
   true
   (:metadata e)
   nil nil))

(schema/defn ^:always-validate
  set-entry-parent :- Entry
  "set-parent-entry creates a copy of entry with its parent set to
  from. This is used when making proxies, and is done before entries
  hit the database."
  [entry :- Entry
   from  :- (schema/maybe schema/Str)]
  (Entry.
   (:id entry)
   (:target entry)
   (:proxy entry)
   (:metadata entry)
   (when (uuid? from) from)
   nil))




