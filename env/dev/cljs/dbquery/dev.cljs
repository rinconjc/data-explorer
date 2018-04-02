(ns ^:figwheel-no-load dbquery.dev
  (:require [dbquery.core :as core]
            [figwheel.client :as figwheel :include-macros true]))

(enable-console-print!)

(figwheel/watch-and-reload
  :websocket-url "ws://localhost:3450/figwheel-ws"
  :jsload-callback core/mount-root)

(core/init!)
