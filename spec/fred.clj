(ns fred
  ;; TODO: pattern dispatcher doesn't eval at all, make it eval or
  ;; update the comment below
  ;;
  ;; Used a non-standard prefix for rook, to demonstrate that the eval occurs
  ;; in the fred namespace, not somewhere where there's a rook alias already.
  {:arg-resolvers [(io.aviso.rook/build-map-arg-resolver {:partner :barney})]}
  (:require
    [clojure.core.async :refer [go]]
    [io.aviso.rook :as r]
    [io.aviso.rook
     [client :as c]
     [utils :as utils]]))

(defn index
  [loopback-handler partner]
  (go
    (-> (c/new-request loopback-handler)
        (c/to :get partner)
        c/send
        (c/then (response
                  (utils/response {:message (format "%s says `%s'" partner (:message response))}))))))
