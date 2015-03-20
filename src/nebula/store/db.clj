(ns nebula.store.db
  (:require [nebula.store.entry :as entry]
            [nebula.store.blob :as blob]
            [nebula.store.util :as util]
            [clojure.java.jdbc :as jdbc]
            [schema.core :as schema]
            [honeysql.core :as sql]
            [honeysql.helpers :as sql-helpers]))

(def +db-path+ "nebula-store/nebula.db")
(def +db-specs+ {:classname   "org.sqlite.JDBC"
                 :subprotocol "sqlite"
                 :subname     +db-path+})

(defmacro sql-map [q]
  `(-> ~q
       sql/build
       sql/format))

(def last-row-id
  (schema/maybe schema/Int))

(schema/defn ^:always-validate
  store-metadata :- last-row-id
  "store-metadata writes the metadata to the database."
  [md :- entry/sMetadata]
  (let [row-id (jdbc/insert!
                +db-specs+
                :nebula_metadata
                {:created (:created md)})]
    (util/log-info "store metadata: " row-id)
    (when (seq? row-id)
      ((keyword "last_insert_rowid()") ; sqlite driver returns an invalid
       (first row-id)))))              ; keyword, so this is a workaround
                                       ; for that.


(schema/defn ^:always-validate
 store-entry :- (schema/maybe schema/Int)
 "store-entry writes the entry to the database."
 [e :- entry/sEntry]
 (let [md-result (store-metadata (:metadata e))
       proxy     (if (:proxy e) 1 0)]   
   (when-not (nil? md-result)
     (let [entry-result
           (jdbc/insert! +db-specs+
                         :nebula_entries
                         {:uuid     (:id e)
                          :target   (:target e)
                          :metadata md-result
                          :proxy    proxy
                          :parent   (:parent e)})]
       (util/log-info "store entry: " entry-result)
       (when (seq? entry-result)
         ((keyword "last_insert_rowid()") (first entry-result)))))))

(declare uuid-is-blob)
(schema/defn ^:always-validate
  lookup-entry :- (schema/maybe entry/sEntry)
  [uuid :- schema/Str]
  (when (entry/uuid? uuid)
    (let [entry (jdbc/query
                 +db-specs+
                 (sql-map
                  {:select [:uuid :target :proxy :parent :created]
                   :from   [:nebula-entries]
                   :where  [:= :nebula-entries.uuid uuid]
                   :join   [:nebula-metadata [:= :nebula-entries.metadata
                                              :nebula-metadata.id]]}))]
      (util/log-info "lookup-entry: " (type entry))
      (when (and (seq? entry) (seq entry))
        (let [entry (first entry)]
          (entry/map->Entry  ; TODO: this is a lot of repetitive code.
           {:id     (:uuid entry)       ; macro it out.
            :target (:target entry)
            :parent (:parent entry)
            :proxy  (if (zero? (:proxy entry)) false true)
            :metadata (entry/->Metadata (:created entry))}))))))

(schema/defn ^:always-validate
  uuid-is-blob? :- schema/Bool
  "Given a UUID representing an entry and a SHA-256 identifier for a blob,
check whether the blob is referenced by the identifier."
  [id   :- schema/Str
   blob :- schema/Str]
  (if (and (entry/uuid? id) (blob/valid-sha256? blob))
    (let [entry (lookup-entry id)]
      (if entry
        (= blob (:target entry))
        false))
    false))

(defn- non-zero?
  "Given a database count, return whether it is non-zero."
  [res]
  (util/log-info "non-zero? " res)
  (let [res (if (seq? res) (first res) res)]
    (not
     (zero? res))))

(schema/defn ^:always-validate
  count-references
  "Count the number of entries pointing to a specific blob."
  [blob-id :- schema/Str]
  ((keyword "count(*)")
   (first
    (jdbc/query +db-specs+
                (sql/format
                 {:select [:%count.*]
                  :from [:nebula_entries]
                  :where [:= :target blob-id]})))))

(schema/defn ^:always-validate
  garbage-collect-entry
  [entry :- entry/sEntry]
  (let [blob-id (:target entry)]
    (when (blob/valid-sha256? blob-id)
      (if (zero? (count-references blob-id))
        (blob/remove-blob blob-id)))))

(defn map-targets
  [results]
  (map :uuid results))

(declare delete-entry)
(defn garbage-collect-references
  [uuid]
  (let [targets (jdbc/query +db-specs+
                            (sql/format
                             (sql/build
                              {:select :uuid
                               :from :nebula-entries
                               :where [:= :target uuid]})))]
    (doseq [target (map :uuid targets)]
      (delete-entry target))))

;;; TODO: add garbage collection
(schema/defn ^:always-validate
  delete-entry :- schema/Bool
  "delete-entry removes the entry named by the UUID from the
  database."
  [uuid :- schema/Str]
  (when (entry/uuid? uuid)
    (let [entry (lookup-entry uuid)]
      (if entry
        (do
          (util/log-info "delete entry: "
                (jdbc/delete! +db-specs+
                              :nebula_entries
                              ["uuid = ?" uuid]))

          ;; this should delete any entries referencing this one
          (util/log-info "garbage collect refs: "(garbage-collect-references uuid))
          (util/log-info "garbage collect blob: " (garbage-collect-entry entry))
          true)
        false))))
