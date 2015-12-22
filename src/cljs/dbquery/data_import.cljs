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

(defn new-table-with-fields [import-form data dest-error]
  (let [data-types (atom nil)]

    (GET (str "/ds/" (:database @import-form) "/data-types") :response-format :json
         :handler #(reset! data-types %) :error-handler #(reset! dest-error (error-text %)))

    (fn [import-form data dest-error]
      [:div.table-responsive
       [:table.table-bordered.table-stripped.summary
        [:thead [:tr [:th "#"] [:th "Dest. Column"] [:th "Data Type"] [:th "Size"]
                 [:th "Source Column"] [:th "Format"] [:th "Sequential"]]]
        [:tbody
         (doall (map-indexed
                 (fn [i col] ^{:key i}
                   [:tr
                    [:td [button
                          {:on-click #(doto import-form
                                        (swap! :columns (fn [form] (remove-nth (:columns form) i)))
                                        (swap! :mappings (fn [form] (remove-nth (:mappings form) i))))
                           :title "Remove" :bsStyle "primary"}
                          [:i.fa.fa-minus]]]
                    [:td [bare-input {:type "text" :model [import-form :columns i :column_name] :size 20}]]
                    [:td [bare-input {:type "select" :model [import-form :columns i :type]
                                      :options (for [t @data-types] [(t "data_type") (t "type_name")])}]]
                    [:td (if (size-required? (get-in @import-form [:columns i :type]))
                           [bare-input {:type "text" :model [import-form :columns i :size] :size 5}])]
                    [:td [bare-input {:type "select" :model [import-form :mappings i :source]
                                      :options (for [h (@data "header")] [h h])}]]
                    [:td (if (format-required? (get-in @import-form [:columns i :type]))
                           [bare-input {:type "text" :model [import-form :mappings i :format]}])]
                    [:td]]) (@import-form :columns)))]
        [:tfoot
         [:tr [:td {:col-span 7}
               [button {:on-click #(doto import-form
                                     (swap! (fn [form] (assoc form :columns (conj (:columns form) {}))))
                                     (swap! (fn [form] (assoc form :mappings (conj (:mappings form) {})))))
                        :title "Add column" :bsStyle "default"}
                [:i.fa.fa-plus]]]]]]])))

(defn table-mappings [import-form data dest-error]
  (let [headers (@data "header")]

    (GET (str "/ds/" (:database @import-form) "/tables/" (:table @import-form))
         :response-format :json
         :handler #(swap! import-form assoc :mappings
                          (into {} (for [c (% "columns") :let [{:strs[name data_type]} c]]
                                     [name {:type data_type}])))
         :error-handler #(reset! dest-error (error-text %)))

    (fn [import-form data dest-error]
      [:div.table-responsive
       [:table.table-bordered.table-stripped.summary
        [:thead
         [:tr [:th "Dest. Column"] [:th "Source Column"] [:th "Format"] [:th "Sequential?"]]]
        [:tbody
         (for [[name {:keys[type]}] (@import-form :mappings)]
           ^{:key name}
           [:tr
            [:td name]
            [:td
             [bare-input {:type "select" :model [import-form :mappings name :source]
                          :options (for [h headers][h h])}]]
            [:td
             (if (format-required? type)
               [bare-input {:type "text" :model [import-form :mappings name :format] :size 12}])]
            [:td ]])]]])))

(defn import-dest-form [data]
  (let [dest-error (atom nil)
        tables (atom nil)
        data-sources (atom nil)
        import-form (atom {:columns [] :mappings []})]

    (GET "/data-sources" :response-format :json
         :handler #(do (reset! data-sources %) (reset! dest-error nil))
         :error-handler #(reset! dest-error (error-text %)))

    (add-watch import-form :key
               (fn [k r {old-db :database old-table :table} {new-db :database new-table :table}]
                 (when (not= old-db new-db)
                   (GET (str "/ds/" new-db "/tables") :response-format :json
                        :handler #(reset! tables %) :error-handler #(reset! dest-error (error-text %))))))
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
        (cond
          (= "_" (@import-form :table)) [new-table-with-fields import-form data dest-error]
          (some? (@import-form :table)) [table-mappings import-form data dest-error])]])))

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
