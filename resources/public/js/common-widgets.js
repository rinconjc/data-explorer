angular.module('common-widgets', [])
    .factory('focus', function ($rootScope, $timeout) {
        return function(name) {
            $timeout(function (){
                $rootScope.$broadcast('focusOn', name);
            });
        };
    }).directive('focusOn', function() {
        return function(scope, elem, attr) {
            scope.$on('focusOn', function(e, name) {
                if(name === attr.focusOn) {
                    elem[0].focus();
                }
            });
        };
    })
    .factory('RecursionHelper', ['$compile', function($compile){
        return {
            /**
             * Manually compiles the element, fixing the recursion loop.
             * @param element
             * @param [link] A post-link function, or an object with function(s) registered via pre and post properties.
             * @returns An object containing the linking functions.
             */
            compile: function(element, link){
                // Normalize the link parameter
                if(angular.isFunction(link)){
                    link = { post: link };
                }

                // Break the recursion loop by removing the contents
                var contents = element.contents().remove();
                var compiledContents;
                return {
                    pre: (link && link.pre) ? link.pre : null,
                    /**
                     * Compiles and re-adds the contents
                     */
                    post: function(scope, element){
                        // Compile the contents
                        if(!compiledContents){
                            compiledContents = $compile(contents);
                        }
                        // Re-add the compiled contents to the element
                        compiledContents(scope, function(clone){
                            element.append(clone);
                        });

                        // Call the post-linking function, if any
                        if(link && link.post){
                            link.post.apply(null, arguments);
                        }
                    }
                };
            }
        };
    }])
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
            template:'<table class="table {{class}}" data-len="{{columns.length}}"><thead><tr><th width="1px;"></th><th ng-repeat="col in columns track by $index">{{col}} <sort-button field="{{$index}}" sort-fn="sorter"/></th></tr></thead><tbody><tr ng-repeat="row in data"><td>{{$index+1}}</td><td ng-repeat="item in row | toArray:columns track by $index" title="{{item}}">{{item}}</td></tr></tbody><tfoot ng-if="hasMore()"><tr><td colspan="{{columns.length+1}}"><a ng-click="showMoreFn()" class="btn btn-lnk">More <span class="glyphicon glyphicon-menu-down"></span></a></td></tr></tfoot></table>',
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
    .directive('menuBar', function(RecursionHelper){
        return {
            restrict:'A',
            scope:{
                model:'='
            },
            transclude:true,
            templateUrl:'tpls/menu-bar.html',
            controller:function($scope, $rootScope){
                $scope.fireEvent = function(evt){
                    $rootScope.$emit(evt);
                };
            },
            compile:function(element){
                return RecursionHelper.compile(element);
            }
        };
    })
    .directive('dataForm', function(){
        return {
            scope:{
                meta:'=',
                model:'='
            },
            templateUrl:'tpls/form.html',
            controller:function($scope){

            }
        };
    })
    .directive('formField', function(){
        return {
            scope:{
                meta:'=',
                model:'='
            },
            replace:true,
            templateUrl:'tpls/form-field.html',
            controller:function($scope){

            }
        }
    })
    .directive('fileModel', function ($parse) {
        return {
            restrict: 'A',
            link: function(scope, element, attrs) {
                var model = $parse(attrs.fileModel);
                var modelSetter = model.assign;

                element.bind('change', function(){
                    scope.$apply(function(){
                        modelSetter(scope, element[0].files[0]);
                    });
                });
            }
        };
    })
;
