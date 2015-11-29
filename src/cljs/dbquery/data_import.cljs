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
        error (atom nil)]
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
                [:tr (for [v row]^{:key v}[:td v])]) (@data "rows"))]]]])])))
