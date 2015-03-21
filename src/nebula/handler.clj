(ns nebula.handler
  (:use [hiccup.core :only [html]])
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [nebula.store.core :as store]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [taoensso.timbre :as timbre]
            ring.middleware.params
            byte-streams))

(timbre/refer-timbre)
(defroutes app-routes
  (GET "/" [_] 
       (html
        [:html
         [:head
          [:title "Nebula Demo"]]
         [:body
          [:h1 "WILLKOMMEN ZU NEBULA"]
          [:p "Endpoints"]
          [:ul
           [:li "POST /entry :: upload new blob"
            [:ul
             [:li "put the file contents in the \"file\" parameter"]
             [:li [:code "curl -F file=@file.txt /entry"]]
             [:li "returns the UUID of the file entry. don't lose this, as otherwise
              this is the only way to access the file."]]]
           [:li "GET /entry/:uuid :: retrieve the blob stored under uuid."
            [:ul
             [:li "for example, if the upload returned the UUID 2181203d-7c99-4cf3-8461-f0702565819b,"]
             [:li [:code "curl /entry/2181203d-7c99-4cf3-8461-f0702565819b"]]
             [:li "currently serves the file as application/octet-stream (I think)"]]]
           [:li "POST /entry/:uuid :: upload new blob with parent"
            [:ul
             [:li "this uploads some data with `uuid` as the parent entry. for example, with the previous UUID,"]
             [:li [:code "curl -F file=@updated-file.txt /entry/2181203d-7c99-4cf3-8461-f0702565819b"]]
             [:li "this also returns the UUID of the created file."]]
            [:li "GET /entry/:uuid/proxy :: proxy an entry"
             [:ul
              [:li "use this to share a file without giving away its original UUID"]
              [:li "returns the UUID of the proxy"]
              [:li [:code "curl /entry/2181203d-7c99-4cf3-8461-f0702565819b/proxy"]]]]]
           [:li "DELETE /entry/:uuid :: remove an entry"
            [:ul
             [:li "remove the entry from the store"]
             [:li "this will perform garbage collection, removing the backing blob if it's no longer needed and any proxied entries that will now be invalidated."]
             [:li "returns the UUID of the deleted entry"]
             [:li [:code "curl -X DELETE /entry/2181203d-7c99-4cf3-8461-f0702565819b"]]]]
           [:li "GET /entry/:uuid/lineage :: retrieve an entry's lineage"
            [:ul
             [:li "returns a list of UUIDs representing the history of the entry"]
             [:li [:code "curl /entry/2181203d-7c99-4cf3-8461-f0702565819b/lineage"]]]]]]]))
  
  (GET "/entry/:uuid" {{uuid :uuid} :params}
       (info (str "GET /entry/" uuid))
       (store/resolve-target uuid))
  (POST "/entry/:uuid" {{uuid :uuid file :file} :params}
        (info (str "POST /entry/" uuid))
        (store/upload-blob
         (byte-streams/to-byte-array
          (byte-streams/to-byte-array
           (:tempfile file)))
         uuid))
  (GET "/entry/:uuid/info" {{uuid :uuid} :params}
       (info (str "GET /entry/" uuid "/info"))
       (store/entry-info uuid))
  (POST "/entry" {{file :file} :params}
        (info "POST /entry")
        (store/upload-blob
         (byte-streams/to-byte-array (byte-streams/to-byte-array (:tempfile file)))
         nil))
  (GET "/entry/:uuid/proxy" {{uuid :uuid} :params}
       (info "GET /entry/" uuid "/proxy")
       (store/proxy-entry uuid))
  (DELETE "/entry/:uuid" {{uuid :uuid} :params}
          (info (str "DELETE /entry" uuid))
          (store/delete-entry uuid))
  (GET "/entry/:uuid/lineage" {{uuid :uuid} :params}
       (info (str "GET /entry/" uuid "/lineage"))
       (store/entry-history uuid))
  (route/not-found (do
                     (info "not found")
                     "Not Found")))

(def app
  (wrap-defaults app-routes
                 ;; security disabled while I figured out the
                 ;; anti-forgery tokens
                 (dissoc site-defaults :security)))
