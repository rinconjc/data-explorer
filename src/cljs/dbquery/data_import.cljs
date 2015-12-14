(ns dbquery.data-import
  (:require [reagent.core :as r :refer [atom]]
            [dbquery.commons :as c :refer [input bare-input button error-text alert
                                           remove-nth]]
            [ajax.core :refer [GET POST]]
            [clojure.walk :refer [postwalk]]))

(defn- upload-file [params data error]
  (POST "/upload" :body (reduce #(doto %1 (.append (name (first %2)) (second %2))) (js/FormData.) params)
        :response-format :json
        :handler #(reset! data %)
        :error-handler #(reset! error (error-text %))))

(defn size-required? [type]
  (#{2 3 1 12} type))

(defn format-required? [type]
  (#{91 92 93 3 8 2 7} type))

(defn import-dest-form [data]
  (let [dest-error (atom nil)
        tables (atom nil)
        columns (atom nil)
        data-sources (atom nil)
        import-form (atom {:columns [] :mappings []})
        data-types (atom nil)]

    (GET "/data-sources" :response-format :json
         :handler #(do (reset! data-sources %) (reset! dest-error nil))
         :error-handler #(reset! dest-error (error-text %)))

    (add-watch import-form :key
               (fn [k r {old-db :database old-table :table} {new-db :database new-table :table}]
                 (when (not= old-db new-db)
                   (GET (str "/ds/" new-db "/tables") :response-format :json
                        :handler #(reset! tables %) :error-handler #(reset! dest-error (error-text %)))
                   (GET (str "/ds/" new-db "/data-types") :response-format :json
                        :handler #(reset! data-types %) :error-handler #(reset! dest-error (error-text %))))
                 (when (not= old-table new-table)
                   (if (= new-table "_")
                     (reset! columns nil)
                     (GET (str "/ds/" new-db "/tables/" new-table) :response-format :json
                          :handler #(reset! columns %) :error-handler #(reset! dest-error (error-text %)))))))
    (fn [data]
      [:div
       [:h4 "Preview Data:"]
       [:div.table-responsive
        [:table.table-bordered.table-stripped.summary
         [:thead [:tr
                  (for [c (@data "header")] ^{:key c} [:th c])]]
         [:tbody (map-indexed
                  (fn [i row] ^{:key i}
                    [:tr (map-indexed
                          (fn [j v] ^{:key j} [:td v]) row)]) (@data "rows"))]]]
       [:h4 "Import to:"]
       [:form.form-inline
        [input {:type "select" :label "Database: " :model [import-form :database]
                :options (for [db @data-sources] [(db "id") (db "name")])}]
        [input {:type "select" :label "Table: " :model [import-form :table]
                :options (for [t @tables] [(t "name") (t "name")])}
         ^{:key "_"} [:option {:value "_"} "<New Table>"]]
        (if (= "_" (:table @import-form))
          [input {:type "text" :placeholder "New table name" :model [import-form :newTable]}])
        [:span.text-danger @dest-error]]
       [:div
        [:h4 "Field Mapping:"]
        (if (= "_" (@import-form :table))
          [:table.table-bordered.table-stripped.summary
           [:thead [:tr [:th "#"] [:th "Dest. Column"] [:th "Data Type"] [:th "Size"]
                    [:th "Source Column"] [:th "Format"] [:th "Sequential"]]]
           [:tbody
            (doall (map-indexed
                    (fn [i col] ^{:key i}
                      [:tr
                       [:td [button
                             {:on-click #(doto import-form
                                           (swap! :columns (fn[form] (remove-nth (:columns form) i)))
                                           (swap! :mappings (fn[form] (remove-nth (:mappings form) i))))
                              :title "Remove" :bsStyle "primary"}
                             [:i.fa.fa-minus]]]
                       [:td [bare-input {:type "text" :model [import-form [:columns i :column_name]] :size 20}]]
                       [:td [bare-input {:type "select" :model [import-form [:columns i :type]]
                                         :options (for [t @data-types] [(t "data_type") (t "type_name")])}]]
                       [:td (if (size-required? (get-in @import-form [:columns i :type]))
                              [bare-input {:type "text" :model [import-form [:columns i :size]] :size 5}])]
                       [:td [bare-input {:type "select" :model [import-form [:mappings i :source]]
                                         :options (for [h (@data "header")] [h h])}]]
                       [:td (if (format-required? (get-in @import-form [:columns i :type]))
                              [bare-input {:type "text" :model [import-form [:mappings i :format]]}])]
                       [:td]]) (@import-form :columns)))]
           [:tfoot
            [:tr [:td {:col-span 7}
                  [button {:on-click #(doto import-form
                                        (swap! (fn [form] (assoc form :columns (conj (:columns form) {}))))
                                        (swap! (fn [form] (assoc form :mappings (conj (:mappings form) {})))))
                           :title "Add column" :bsStyle "default"}
                   [:i.fa.fa-plus]]]]]])]])))

(defn import-data-tab []
  (let [upload-params (atom {:separator ","})
        data (atom nil)
        upload-error (atom nil)]
    (fn []
      [:div
       (if @upload-error
         [alert {:bsStyle "danger"} @upload-error])
       [:form.form-inline
        [input {:type "file" :model [upload-params :file]
                :className "form-control" :label "CSV File:"}]
        [input {:type "select" :model [upload-params :separator]
                :label "Separator" :options [["\t" "Tab"] ["," ","]]}]
        [input {:type "checkbox" :model [upload-params :hasHeader]
                :label "Has Header?"}]
        [button {:bsStyle "primary" :on-click #(upload-file @upload-params data upload-error)} "Upload"]]
       (if @data
         [import-dest-form data])])))
