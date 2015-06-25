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
    .directive('spinner', function(){
        return {
            scope:{
                hideWhen:'='
            },
            template:'<div class="text-center" style="padding-top:40px;" ng-hide="hideWhen"><h2><span class="glyphicon glyphicon-refresh spinning"></span></h2></div>'
        };
    })
    .directive('sortButton', function(){
        return {
            scope:{
                field:'@',
                sortFn:'='
            },
            template:'<button ng-click="sort()" class="btn btn-xs btn-link"><span class="glyphicon" ng-class="sortClass"></span></button>',
            controller:function($scope){
                var nextSort={'':'+','+':'-','-':''};
                var sortClass={'':'glyphicon-sort', '+':'glyphicon-triangle-bottom','-':'glyphicon-triangle-top'};
                $scope.sortState='';
                $scope.sortClass=sortClass[$scope.sortState];
                $scope.sort = function(){
                    $scope.sortState = nextSort[$scope.sortState];
                    $scope.sortFn($scope.field, $scope.sortState);
                    $scope.sortClass=sortClass[$scope.sortState];
                };
            }
        };
    })
    .directive('ngTable',function(){
        return {
            scope:{
                columns:'=',
                data:'=',
                class:'@'
            },
            replace:true,
            transclude:true,
            template:'<table class="table {{class}}" data-len="{{columns.length}}"><thead><tr><th ng-repeat="col in columns">{{col}} <sort-button field="{{col}}" sort-fn="sorter"/></th></tr></thead><tbody><tr ng-repeat="row in data | orderBy:sortState"><td ng-repeat="item in row track by $index" title="{{item}}">{{item}}</td></tr></tbody></table>',
            controller:function($scope){
                $scope.sortState=[];
                $scope.sorter = function(col, ascDesc){
                    console.debug('sorting by', col, ascDesc);
                    if(ascDesc=='+'){
                        $scope.sortState.push(col);
                    } else if(ascDesc=='-'){
                        var i=$scope.sortState.indexOf(col);
                        if(i>=0){
                            $scope.sortState[i] = '-'+col;
                        }
                    } else{
                        var i=$scope.sortState.indexOf('-'+col);
                        if(i>=0)
                            $scope.sortState.splice(i,1);
                    }
                    console.debug('sort state:', $scope.sortState);
                };
            }
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
