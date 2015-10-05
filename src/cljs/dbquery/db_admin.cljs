(ns dbquery.db-admin
  (:require [dbquery.commons :as c]
            [reagent.core :as r :refer [atom]]
            [ajax.core :refer [GET POST]]))

(defn database-window [show? db-spec]
  [c/modal {:on-hide #(reset! show? false) :show @show?}
   [c/modal-header
    [:h4 "Database Connection"]]
   [c/modal-body
    [:div.container-fluid
     [:form {:class-name "form-horizontal"}
      [c/input (c/bind-value db-spec :name :type "text"
                             :label "Connection Name"
                             :placeholder "Unique name"
                             :label-class-name "col-sm-4"
                             :wrapper-class-name "col-sm-6")]
      [c/input (c/bind-value db-spec :dbms :type "select":id :dbms
                             :label "Database Type"
                             :placeholder "Select database type"
                             :label-class-name "col-sm-4"
                             :wrapper-class-name "col-sm-6")
       [:option {:disabled ""} ""]
       [:option {:value "ORACLE"} "ORACLE"]
       [:option {:value "H2"} "H2"]]
      [c/input (c/bind-value db-spec :url
                             :type "text" :label "URL"
                             :placeholder "<server>:<port>..."
                             :label-class-name "col-sm-4"
                             :wrapper-class-name "col-sm-8")]
      [c/input (c/bind-value db-spec :schema
                             :type "text" :label "Schema"
                             :label-class-name "col-sm-4"
                             :wrapper-class-name "col-sm-5")]
      [c/input (c/bind-value db-spec :user_name
                             :type "text" :label "User"
                             :label-class-name "col-sm-4"
                             :wrapper-class-name "col-sm-5")]
      [c/input (c/bind-value db-spec :password
                             :type "text" :label "Password"
                             :label-class-name "col-sm-4"
                             :wrapper-class-name "col-sm-5")]
      ]]]
   [c/modal-footer
    [c/button {:bsStyle "primary"
               :on-click (fn [e] (reset! show? false)

                           (.log js/console "should have closed? " @show?))} "Connect"]]])

(defn select-db-dialog [return-fn]
  (let [show? (atom true)
        db-id (atom nil)
        dbs (atom [])
        handle-ok (fn [_]
                    (reset! show? false)
                    (return-fn @db-id))]
    (GET "/data-sources" :response-format :json
         :handler #(reset! dbs %)
         :error-handler #(js/console.log "failed retrieving dbs..." %))
    (fn []
      [c/modal {:show @show? :on-hide #(reset! show? false) :bsSize "small"}
       [c/modal-header
        [:h4 "Open Database"]]
       [c/modal-body
        [:div.container-fluid
         [:form.form-horizontal {:on-submit handle-ok}
          [c/input {:value @db-id :type "select" :label "Database"
                    :on-change #(reset! db-id (-> % .-target .-value))}
           [:option {:disabled true}  "Select a Database"]
           (for [db @dbs]
             ^{:key db}[:option {:value (db "id")} (db "name")])]]]]
       [c/modal-footer
        [c/button {:bsStyle "primary"
                   :on-click handle-ok} "OK"]]])))
