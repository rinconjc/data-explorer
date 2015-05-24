describe('data-table-directives', function(){
    var element, scope;// $httpBackend;

    beforeEach(module('data-table'));

    beforeEach(inject(function($rootScope, $compile, $injector, $log) {
        // Set up the mock http service responses
        //$httpBackend = $injector.get('$httpBackend');
        scope = $rootScope.$new();
        scope.columns = ["Col1", "Col2"];
        scope.rows = [['Value 1,1', 'Value 1,2'], ['Value 2,1', 'Value 2,2']];

        element = '<ng-table class="table-bordered" columns="columns" data="rows" />';

        element = $compile(element)(scope);
        scope.$digest();        
    }));

    afterEach(function() {
        //$httpBackend.verifyNoOutstandingExpectation();
        //$httpBackend.verifyNoOutstandingRequest();
    });

    describe('data-table', function(){

        it('should render data', function(){
            var isolated = element.isolateScope();
            console.debug('elem html', element.html());
            expect(isolated.columns.length).toBe(2);
        });
        
        it('should render children', function(){
            console.debug('html', element.html());
            expect(element.find('tr').length).toBe(3);
        });
    });
    

});
