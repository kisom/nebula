(ns nebula.store.blob
  (:require [pandect.algo.sha256 :as sha256]
            [schema.core :as schema]
            byte-streams)
  (:use [clojure.string :only [join]]
        [clojure.java.io :only [as-file delete-file]]))

(def ^{:doc "Default root for blobs to be stored in."}
  store-path "nebula-store")

(schema/defn hexify :- schema/Str
  "hexify base-16 encodes its input (which should be a byte array), returning
  a string. For example,

  (hexify (.getBytes \"hi\")) ;; returns \"6869\"
  "
  [data :- bytes]
  (join
    (map #(format "%02x" %) data)))


(schema/defn ^:always-validate
  sha256-hexdigest :- schema/Str
  "sha256-hexdigest returns the hex-encoded SHA-256. For example,

  (sha256-hexdigest (.getBytes \"hello, world\")

  returns

  \"09ca7e4eaa6e8ae9c7d261167129184883644d07dfba7cbfbc4c8a2e08360d5b\"
  "
  [blob :- bytes]
  (join
   (doall
    (map str
         (hexify
          (sha256/sha256-bytes blob))))))

(schema/defn ^:always-validate
  valid-sha256? :- schema/Bool
  "valid-sha256? returns true if the string is a SHA-256 sized string
  containing only hex-encoded values."
  [s :- (schema/maybe schema/Str)]
  (if (string? s)
    (not
     (nil?
      (re-seq #"[0-9a-f]{64}" s)))
    false))

(schema/defn ^:always-validate
  split-digest :- (schema/maybe java.io.File)
  "split-digest takes a hex-encoded SHA-256 digest and returns the
  path that the blob should be stored in (with a base of
  store-path. For example, if *store-path* is \"nebula-store\",

  ```
  (def *hello-world* (.getBytes \"hello, world\"))
  (split-digest *hello-world*)
  ;; returns #<File nebula-store/09/ca/7e/4e/aa/6e/
  ;; 8a/e9/c7/d2/61/16/71/29/18/48/83/64/4d/07/df/
  ;; ba/7c/bf/bc/4c/8a/2e/08/36/0d/5b>
  ```
  "
  [digest :- schema/Str]
  (as-file
   (str store-path "/"
        (join "/"
              (map
               join
               (partition 2 digest))))))

(schema/defn ^:always-validate
  read-blob :- (schema/maybe java.io.File)
  "read-blob looks up the file referenced by the SHA-256
  identifier. It returns a file handle, not the actual contents, so
  the caller can more effectively transfer the data."
  [id :- schema/Str]
  (when (valid-sha256? id)
    (let [path (split-digest id)]
      (when (.exists path)
        path))))

(schema/defn ^:always-validate
  write-blob :- (schema/maybe schema/Str)
  "write-blob determines whether the blob needs to be written to disk;
  if it does, it will be written to the path given by split-digest. Any
  required intermediate directories will be created."
  [blob :- bytes]
  (let [digest (sha256-hexdigest blob)
        blob-path (split-digest digest)]
    (if (.exists blob-path)
      digest
      (do
        (.mkdirs (as-file (.getParent blob-path)))
        (byte-streams/transfer blob blob-path)
        digest))))

(schema/defn ^:always-validate
  remove-blob :- schema/Bool
  "remove-blob deletes the blob referenced by the identifier."
  [id :- schema/Str]
  (let [blob-path (split-digest id)]
    (if (.exists blob-path)
      (delete-file blob-path)
      false)))
