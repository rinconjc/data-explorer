(ns dbquery.macros)

;; --------------- form binding macro ------------
;; (fn [e#] (swap! ~model #(assoc ~field (.-value (.- target e#)))))
(defmacro bind-valuex [model field & attrs]
  (apply hash-map (list* :value `(~field @~model)
                         attrs)))
