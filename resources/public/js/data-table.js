angular.module('data-table',[])
    .directive('ngTable',function(){
        return {
            scope:{
                columns:'=',
                data:'=',
                class:'@'
            },
            replace:true,
            transclude:true,
            template:'<table class="table {{class}}" data-len="{{columns.length}}"><thead><tr><th ng-repeat="col in columns">{{col}}<sort-button1 field="{{col}}" sortFn="sorter"/></th></tr></thead><tbody><tr ng-repeat="row in data | orderBy:sortState"><td ng-repeat="item in row track by $index" title="{{item}}">{{item}}</td></tr></tbody></table>',
            controller:function($scope){
                $scope.sortState=[];
                $scope.sorter = function(col, ascDesc){
                    console.debug('sorting by', col, ascDesc);
                    if(ascDesc=='+'){
                        $scope.sortState.push('+'+col);
                    } else if(ascDesc=='-'){
                        var i=$scope.sortState.indexOf('+'+col);
                        if(i>=0)
                            $scope.sortState[i] = '-'+col;
                    } else{
                        var i=$scope.sortState.indexOf('-'+col);
                        if(i>=0)
                            $scope.sortState.splice(i,1);
                    }
                    console.debug('sort state', $scope.sortState);
                };
            }
        };
    });
