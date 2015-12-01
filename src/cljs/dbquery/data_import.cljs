(ns dbquery.data-import
  (:require [reagent.core :as r :refer [atom]]
            [dbquery.commons :as c :refer [input button error-text]]
            [ajax.core :refer [GET POST]]))

(defn- upload-file [params data error]
  (POST "/upload" :body (reduce #(doto %1 (.append (name (first %2)) (second %2))) (js/FormData.) params)
        :response-format :json
        :handler #(reset! data %)
        :error-handler #(reset! error (error-text %))))

(defn import-data-tab []
  (let [upload-params (atom {:separator ","})
        data (atom nil)
        error (atom nil)
        import-form (atom {})
        data-sources (atom nil)
        tables (atom nil)
        columns (atom nil)]

    (GET "/data-sources" :response-format :json :handler #(reset! data-sources %)
         :error-handler #(reset! error (error-text %)))
    (add-watch import-form :key
               (fn[k r {old-db :database old-table :table} {new-db :database new-table :table}]
                 (when (not= old-db new-db)
                   (GET (str "/ds/" new-db "/tables") :response-format :json
                        :handler #(reset! tables %) :error-handler #(reset! error (error-text %))))
                 (when (not= old-table new-table)
                   (GET (str "/ds/" new-db "/tables/" new-table) :response-format :json
                        :handler #(reset! columns %) :error-handler #(reset! error (error-text %))))))
    (fn[]
      [:div
       [:form.form-inline
        [input {:type "file" :model [upload-params :file]
                :className "form-control" :label "CSV File:"}]
        [input {:type "select" :model [upload-params :separator]
                :label "Separator"}
         ^{:key "\t"}[:option {:value "\t"} "Tab"]
         ^{:key ","}[:option {:value ","} ","]]
        [input {:type "checkbox" :model [upload-params :hasHeader]
                :label "Has Header?"}]
        [button {:bsStyle "primary" :on-click #(upload-file @upload-params data error)} "Upload"]]
       (if @data
         [:div
          [:h3 "Preview"]
          [:div.table-responsive
           [:table.table-bordered.table-stripped.summary
            [:thead [:tr
                     (for [c (@data "header")] ^{:key c}[:th c])]]
            [:tbody (map-indexed
                     (fn[i row]^{:key i}
                       [:tr (for [v row]^{:key v}[:td v])]) (@data "rows"))]]]
          [:h4 "Import to:"]
          [:form.form-inline
           [input {:type "select" :label "Database" :model [import-form :database]}
            (map-indexed
             (fn[i db]^{:key i}
               [:option {:value (db "id")} (db "name")]) @data-sources)]
           [input {:type "select" :label "Table" :model [import-form :table] }
            [:option {:value "_"} "New Table"]
            (map-indexed
             (fn[i table]^{:key i}
               [:option {:value (table "name")} (table "name")]) @tables)]]])])))
