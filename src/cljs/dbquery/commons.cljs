(ns dbquery.commons
  (:require [clojure.string :as str]
            [cljsjs.react-bootstrap]
            [re-frame.core :refer [dispatch]]
            [reagent.core :as r :refer [atom]]
            [reagent.core :as reagent]))

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

(defn open-modal [modal-comp]
  (let [container (js/document.getElementById "modals")]
    (r/unmount-component-at-node container)
    (r/render modal-comp container)))

(defn dispatch-all [ev1 & more]
  (dispatch ev1)
  (doseq [ev more]
    (dispatch ev)))

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

(defn typeahead [{:keys [model choice-fn result-fn data-source] :as attrs
                  :or {choice-fn identity result-fn identity}}]
  (let [[doc & path] model
        typeahead-hidden? (atom true)
        mouse-on-list? (atom false)
        selected-index (atom -1)
        selections (atom [])
        save! #(swap! doc assoc-in path %)
        value-of #(-> % .-target .-value)
        choose-selected #(when (and (not-empty @selections) (> @selected-index -1))
                           (let [choice (nth @selections @selected-index)]
                             (save! (result-fn choice))
                             (choice-fn choice)
                             (if % (reset! typeahead-hidden? true))))]
    (fn [attrs]
      [:span
       [:input.form-control
        (assoc attrs
               :on-focus    #(save! nil)
               :on-blur     #(when-not @mouse-on-list?
                               (reset! typeahead-hidden? true)
                               (reset! selected-index -1))
               :on-change   #(when-let [value (str/trim (value-of %))]
                               (reset! selections (data-source (.toLowerCase value)))
                               (save! (value-of %))
                               (reset! typeahead-hidden? false)
                               (reset! selected-index -1))
               :on-key-down #(do
                               (case (.-which %)
                                 38 (do
                                      (.preventDefault %)
                                      (when-not (= @selected-index 0)
                                        (swap! selected-index dec)
                                        (choose-selected false)))
                                 40 (do
                                      (.preventDefault %)
                                      (when-not (= @selected-index (dec (count @selections)))
                                        (save! (value-of %))
                                        (swap! selected-index inc)
                                        (choose-selected false)))
                                 9  (choose-selected true)
                                 13 (choose-selected true)
                                 27 (do (reset! typeahead-hidden? true)
                                        (reset! selected-index 0))
                                 "default")))]

       [:ul {:style {:display (if (or (empty? @selections) @typeahead-hidden?) :none :block) }
             :class "typeahead-list"
             :on-mouse-enter #(reset! mouse-on-list? true)
             :on-mouse-leave #(reset! mouse-on-list? false)}
        (doall
         (map-indexed
          (fn [index result]
            [:li {:tab-index     index
                  :key           index
                  :class         (if (= @selected-index index) "highlighted" "typeahead-item")
                  :on-mouse-over #(do
                                    (reset! selected-index (js/parseInt (.getAttribute (.-target %) "tabIndex"))))
                  :on-click      #(do
                                    (reset! typeahead-hidden? true)
                                    (save! (result-fn result))
                                    (choice-fn result))}
             (result-fn result)])
          @selections))]])))

(def focus-wrapper
  (with-meta identity
    {:component-did-mount #(.focus (reagent/dom-node %))}))

(defn focus-aware [focus? e]
  (if focus?
    [focus-wrapper e]
    e))

(defn bare-input
  [{:keys[model type options] :as attrs} & children]
  (let [attrs (bind attrs model type)
        children (to-options options children)]
    (case type
      "text" [:input.form-control attrs]
      "password" [:input.form-control attrs]
      "select" [:select.form-control (assoc attrs :default-value (or (:value attrs)  "")) children]
      "textarea" [:textarea.form-control attrs]
      "file" [:input attrs]
      "typeahead" [typeahead (assoc attrs :model model)]
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
                (assoc attrs :on-change
                       (wrap-validator validator
                                       #(reset! valid-class (str "has-" (name %))))) attrs)]
    (fn [{:keys[type label wrapper-class-name label-class-name validator] :as attrs} & children]
      [:div.form-group {:class @valid-class}
       [:label.control-label {:class label-class-name} label]
       (if wrapper-class-name
         [:div {:class wrapper-class-name}
          [focus-aware (:focus attrs) [bare-input attrs children]]]
         [focus-aware (:focus attrs) [bare-input attrs children]])])))

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

(defn update-where [pred xs f]
  (map #(if (pred %) (f %) %) xs))

(defn remove-nth [v i]
  (vec (concat (subvec v 0 i) (subvec v (inc i)))))

(defn progress-overlay []
  [:div {:style {:position "absolute" :width "100%" :height "100%" :z-index 100
                 :top 0 :left 0 :background "rgba(255,255,255,0.5)"
                 :text-align "center"}}
   [:i.fa.fa-spinner.fa-spin.fa-3x {:style {:margin-top "10%"}}]])

(defn error-text [e]
  (or (:response e) (get-in e [:parse-error :original-text])))
