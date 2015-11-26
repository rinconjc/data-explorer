(ns dbquery.data-import
  (:require [reagent.core :as r :refer [atom]]
            [dbquery.commons :as c :refer [input button]]
            [ajax.core :refer [GET POST]]))

(defn- upload-file [params]
  (POST "/upload" :params params :format :raw :response-format :json
        :handler #(.log js/console "success:" %)
        :error-handler #(.log js/console "failure:" %)))

(defn import-data-tab []
  (let [upload-params (atom {})]
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
        [button {:bsStyle "primary" :on-click #(upload-file @upload-params)} "Upload"]]])))
