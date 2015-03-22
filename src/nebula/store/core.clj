(ns nebula.store.core
  (:require [nebula.store.db    :as db]
            [nebula.store.entry :as entry]
            [nebula.store.blob  :as blob]
            [cheshire.core :as json]
            [schema.core :as schema]
            byte-streams)
  (:import  [java.io.File]))

(schema/defn validate-id :- schema/Bool
  [id :- (schema/maybe schema/Str)]
  (if (nil? id)
    true
    (entry/uuid? id)))

(schema/defn ^:always-validate
  resolve-target :- (schema/maybe java.io.File)
  "resolve-target takes a UUID, and attempts to retrieve the blob it
  refers to."
  [uuid :- schema/Str]
  (when (validate-id uuid)
      (let [entry  (db/lookup-entry uuid)
            target (:target entry)]
        (cond
          (entry/uuid? target) (recur target)
          (blob/valid-sha256? target) (blob/read-blob target)
          :else nil))))

(schema/defn ^:always-validate
  entry-info :- (schema/maybe schema/Str)
  "entry-info returns a JSON string containing information about an
  entry. This will remove the target entry, as users should not see
  this information."
  [uuid :- schema/Str]
  (let [entry (db/lookup-entry uuid)]
    (when entry
      (json/generate-string 
       (dissoc entry :target :proxy)))))


;;; TODO: verify that parent exists if not nil
;;; TODO: if the blob already exists, and is referenced by the parent, don't
;;;       updated.
(schema/defn ^:always-validate
  upload-blob :- (schema/maybe schema/Str)
  "upload-blob attempts to store a blob, returning the UUID for the
  new entry created for it."
  [blob   :- bytes
   parent :- (schema/maybe schema/Str)]
  (when (validate-id parent)
    (let [digest (blob/write-blob blob)]
      (when digest
        (let [e (entry/create-entry digest parent)]
          (when (db/store-entry e)
            (:id e)))))))

(schema/defn ^:always-validate
  proxy-entry :- (schema/maybe schema/Str)
  [uuid :- schema/Str]
  (when (validate-id uuid)
    (let [entry (db/lookup-entry uuid)]
      (when entry
        (let [proxied (entry/proxy-entry entry)]
          (when (db/store-entry proxied)
            (:id proxied)))))))

(schema/defn ^:always-validate
  delete-entry :- (schema/maybe schema/Str)
  [uuid :- schema/Str]
  (when (and (validate-id uuid) (db/delete-entry uuid))
    uuid))

(defn load-history
  ([uuid] (load-history uuid []))
  ([uuid lineage]
   (let [entry (db/lookup-entry uuid)]
     (if (and entry (:parent entry))
       (recur (:parent entry) (conj lineage uuid))
       (conj lineage uuid)))))

(defn- build-proxy-list
  ([proxy-list] (build-proxy-list proxy-list []))
  ([proxy-list proxied] 
   ;; (map (comp db/lookup-entry entry/proxy-entry)
   ;;      (load-history uuid []))
   (let [entry (first proxy-list)]
     (if (not (empty? (rest proxy-list)))
       (let [parent (nth proxy-list 1)]
         (recur (rest proxy-list)
                (conj proxied (entry/set-entry-parent
                               entry
                               (when-not (nil? parent)
                                 (:id parent))))))
       (conj proxied entry)))))

(defn proxy-all
  [uuid]
  (let [proxied (build-proxy-list
                 (map (comp entry/proxy-entry db/lookup-entry)
                      (load-history uuid)))]
    (map db/store-entry proxied)
    (json/generate-string
     (map :id proxied))))

(schema/defn ^:always-validate
  entry-history
  [uuid :- schema/Str]
  (when (entry/uuid? uuid)
      (json/generate-string (load-history uuid))))

