(ns dbquery.commons
  (:require [reagent.core :as r :refer [atom]]
            [cljsjs.react-bootstrap]))

(def navbar (r/adapt-react-class js/ReactBootstrap.Navbar))
(def nav-brand (r/adapt-react-class js/ReactBootstrap.NavBrand))
(def nav (r/adapt-react-class js/ReactBootstrap.Nav))
(def nav-item (r/adapt-react-class js/ReactBootstrap.NavItem))
(def nav-dropdown (r/adapt-react-class js/ReactBootstrap.NavDropdown))
(def menu-item (r/adapt-react-class js/ReactBootstrap.MenuItem))
(def modal (r/adapt-react-class js/ReactBootstrap.Modal))
(def modal-header (r/adapt-react-class js/ReactBootstrap.Modal.Header))
(def modal-body (r/adapt-react-class js/ReactBootstrap.Modal.Body))
(def modal-footer (r/adapt-react-class js/ReactBootstrap.Modal.Footer))
(def button (r/adapt-react-class js/ReactBootstrap.Button))
(def rb-input (r/adapt-react-class js/ReactBootstrap.Input))
(def alert (r/adapt-react-class js/ReactBootstrap.Alert))
(def tabs (r/adapt-react-class js/ReactBootstrap.Tabs))
(def tab (r/adapt-react-class js/ReactBootstrap.Tab))
(def button-toolbar (r/adapt-react-class js/ReactBootstrap.ButtonToolbar))
(def button-group (r/adapt-react-class js/ReactBootstrap.ButtonGroup))
(def split-button (r/adapt-react-class js/ReactBootstrap.SplitButton))

(def panel (r/adapt-react-class js/ReactBootstrap.Panel))
(def list-group (r/adapt-react-class js/ReactBootstrap.ListGroup))
(def list-group-item (r/adapt-react-class js/ReactBootstrap.ListGroupItem))
(def popover (r/adapt-react-class js/ReactBootstrap.Popover))
(def overlay-trigger (r/adapt-react-class js/ReactBootstrap.OverlayTrigger))

(defn bind [attrs model type]
  (if-let [[doc & path] model]
    (let [on-change (:on-change attrs)]
      (apply assoc (dissoc attrs :model :options)
             (if (= type "file")
               [:on-change (fn [e]
                             (swap! doc assoc-in path (-> e .-target .-files (aget 0)))
                             (if (fn? on-change) (on-change e)))]
               [:value (get-in @doc path)
                :on-change (fn [e]
                             (swap! doc assoc-in path (-> e .-target .-value))
                             (if (fn? on-change) (on-change e)))])))
    attrs))

(defn to-options [opts children]
  (if (or children opts)
    (concat [^{:key ""}[:option {:value "" :disabled true} "Select"]]
            children
            (map-indexed
             (fn [i [k v]]
               ^{:key i} [:option {:value k} v]) opts))))

(defn bare-input
  [{:keys[model type options] :as attrs} & children]
  (let [attrs (bind attrs model type)
        children (to-options options children)]
    (case type
      "text" [:input.form-control attrs]
      "password" [:input.form-control attrs]
      "select" [:select.form-control (assoc attrs :default-value "") children]
      "textarea" [:textarea.form-control attrs]
      "file" [:input attrs]
      [:div {:class type} [:input attrs]])))

(defn wrap-validator [v cont]
  (fn [e]
    (let [r (v (-> e .-target .-value))
          r (if (keyword? r) r
                (if r :success :error))]
      (cont r))))

(defn input
  "[input {:type text :model [doc id] }]
  [input {:type \"select\" :options seq :kv-fn}]"
  [{:keys[type label wrapper-class-name label-class-name validator] :as attrs} & children]
  (let [valid-class (atom nil)
        attrs (if validator
                (assoc attrs :on-change (wrap-validator validator #(reset! valid-class (str "has-" (name %))))) attrs)]
    (fn []
      [:div.form-group {:class @valid-class}
       [:label.control-label {:class label-class-name} label]
       (if wrapper-class-name
         [:div {:class wrapper-class-name}
          [bare-input attrs children]]
         [bare-input attrs children])])))

(defn remove-x [xs x]
  (remove #(= x %) xs))

(defn close-button [close-fn]
  [button {:on-click #(do (close-fn)
                          (doto % .stopPropagation .preventDefault))
           :class "close"}
   [:span "×"]])

(defn index-where [pred xs]
  (cond
    (empty? xs) nil
    (pred (first xs)) 0
    :else (if-let [c (index-where pred (rest xs))](inc c))))

(defn remove-nth [v i]
  (vec (concat (subvec v 0 i) (subvec v (inc i)))))

(defn progress-overlay []
  [:div {:style {:position "absolute" :width "100%" :height "100%" :z-index 100
                 :top 0 :left 0 :background "rgba(255,255,255,0.5)"
                 :text-align "center"}}
   [:i.fa.fa-spinner.fa-spin.fa-3x {:style {:margin-top "10%"}}]])

(defn error-text [e]
  (or (:response e) (get-in e [:parse-error :original-text])))
