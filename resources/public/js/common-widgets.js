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
    .filter('toArray', function(){
        return function(input, keys){
            if(angular.isArray(input)) return input;
            var array = [];
            angular.forEach(keys, function(k){
                array.push(input[k]);
            });
            return array;
        };
    })
    .directive('ngTable',function($filter){
        return {
            scope:{
                columns:'=?',
                keys:'@',
                data:'=',
                class:'@',
                showMoreFn:'&',
                hasMore:'&'
            },
            replace:true,
            transclude:true,
            template:'<table class="table {{class}}" data-len="{{columns.length}}"><thead><tr><th></th><th ng-repeat="col in columns track by $index">{{col}} <sort-button field="{{$index}}" sort-fn="sorter"/></th></tr></thead><tbody><tr ng-repeat="row in data"><td>{{$index+1}}</td><td ng-repeat="item in row | toArray:columns track by $index" title="{{item}}">{{item}}</td></tr></tbody><tfoot ng-if="hasMore()"><tr><td colspan="{{columns.length}}"><a ng-click="showMoreFn()" class="btn btn-lnk">More <span class="glyphicon glyphicon-menu-down"></span></a></td></tr></tfoot></table>',
            controller:function($scope){
                $scope.sortState=null;
                if($scope.keys){
                    $scope.columns = $scope.keys.split(',');
                }
                $scope.sorter = function(col, ascDesc){
                    if(!$scope.sortState){
                        $scope.sortState=[];
                    }

                    var field = $scope.keys?$scope.columns[col]:col;
                    
                    if(ascDesc === '+'){
                        $scope.sortState.push(field);
                    } else if(ascDesc === '-'){
                        var i=$scope.sortState.indexOf(field);
                        if(i>=0){
                            $scope.sortState[i] = '-'+field;
                        }
                    } else{
                        var i=$scope.sortState.indexOf('-'+field);
                        if(i>=0)
                            $scope.sortState.splice(i,1);
                    }
                    console.debug('sort state:', $scope.sortState);
                    $scope.data = $filter('orderBy')($scope.data, $scope.sortState);
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
    .directive('closeButton', function(){
        return {
            restrict:'E',
            scope:{
                close:'&'
            },
            replace:true,
            template:'<button type="button" style="padding-left:10px;" class="close" ng-click="_close($event)"><span aria-hidden="true">×</span><span class="sr-only">Close</span></button>',
            controller:function($scope){
                $scope._close = function(evt){
                    evt.preventDefault();
                    evt.stopPropagation();
                    $scope.close();
                }
            }
        };
    })
;
