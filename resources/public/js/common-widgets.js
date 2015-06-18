angular.module('common-widgets', [])
    .factory('focus', function ($rootScope, $timeout) {
        return function(name) {
            $timeout(function (){
                $rootScope.$broadcast('focusOn', name);
            });
        }
    }).directive('focusOn', function() {
        return function(scope, elem, attr) {
            scope.$on('focusOn', function(e, name) {
                if(name === attr.focusOn) {
                    elem[0].focus();
                }
            });
        };
    })
    .directive('loginForm', function($log){
        return {
            scope:{
                heading:'@',
                useEmail:'@',
                alertText:'=',
                loginFn:'=',
                allowRemember:'='
            },
            templateUrl:'html/login-form.html',
            controller:function($scope){
                $scope.login = {}
                $scope._doLogin=function(loginData){
                    console.debug('param ', loginData, " login ", $scope.login);
                    $scope.clicked=$scope.loginFn(loginData);
                };
            }
        };
    })
    .factory('Switcher', function(){
        return {
            create:function(){
                var Switch = function() {},
                    active=null,
                    _instance=null;
                return {
                    add:function(name){
                        Object.defineProperty(Switch.prototype, name, {
                            get: function() {
                                return name==active;
                            },
                            set: function(val) {
                                if(val) active=name;
                            }
                        });
                        return this;
                    },
                    remove:function(name){
                        delete getInstance()[name];
                    },
                    getInstance:function(){
                        if(_instance==null){
                            _instance = new Switch();
                        }
                        return _instance;
                    }
                };
            }
        };
    })
;
