description("Tests that the PositionOptions.maximumAge parameter is correctly applied.");

var mockLatitude = 51.478;
var mockLongitude = -0.166;
var mockAccuracy = 100.0;

var mockMessage = 'test';

var position;
var error;

function checkPosition(p) {
    debug('');
    position = p;
    shouldBe('position.coords.latitude', 'mockLatitude');
    shouldBe('position.coords.longitude', 'mockLongitude');
    shouldBe('position.coords.accuracy', 'mockAccuracy');
}

function checkError(e) {
    debug('');
    error = e;
    shouldBe('error.code', 'error.POSITION_UNAVAILABLE');
    shouldBe('error.message', 'mockMessage');
}

geolocationServiceMock.then(mock => {
    mock.setGeolocationPermission(true);
    mock.setGeolocationPosition(mockLatitude, mockLongitude, mockAccuracy);

    // Initialize the cached Position
    navigator.geolocation.getCurrentPosition(function(p) {
        checkPosition(p);
        testZeroMaximumAge();
    }, function(e) {
        testFailed('Error callback invoked unexpectedly');
        finishJSTest();
    });

    function testZeroMaximumAge() {
        // Update the position provided by the mock service.
        mock.setGeolocationPosition(++mockLatitude, ++mockLongitude, ++mockAccuracy);
        // The default maximumAge is zero, so we expect the updated position from the service.
        navigator.geolocation.getCurrentPosition(function(p) {
            checkPosition(p);
            testNonZeroMaximumAge();
        }, function(e) {
            testFailed('Error callback invoked unexpectedly');
            finishJSTest();
        });
    }

    function testNonZeroMaximumAge() {
        // Update the mock service to report an error.
        mock.setGeolocationPositionUnavailableError(mockMessage);
        // The maximumAge is non-zero, so we expect the cached position, not the error from the service.
        navigator.geolocation.getCurrentPosition(function(p) {
            checkPosition(p);
            testNegativeValueMaximumAge();
        }, function(e) {
            testFailed('Error callback invoked unexpectedly');
            finishJSTest();
        }, {maximumAge: 1000});
    }

    function testNegativeValueMaximumAge() {
        // Update the position provided by the mock service.
        mock.setGeolocationPosition(++mockLatitude, ++mockLongitude, ++mockAccuracy);
        // The maximumAge is same as zero, so we expect the updated position from the service.
        navigator.geolocation.getCurrentPosition(function(p) {
            checkPosition(p);
            testOverSignedIntMaximumAge();
        }, function(e) {
            testFailed('Error callback invoked unexpectedly');
            finishJSTest();
        }, {maximumAge: -1000});
    }

    function testOverSignedIntMaximumAge() {
        // Update the mock service to report an error.
        mock.setGeolocationPositionUnavailableError(mockMessage);
        // The maximumAge is non-zero, so we expect the cached position, not the error from the service.
        navigator.geolocation.getCurrentPosition(function(p) {
            checkPosition(p);
            testOverUnsignedIntMaximumAge();
        }, function(e) {
            testFailed('Error callback invoked unexpectedly');
            finishJSTest();
        }, {maximumAge: 2147483648});
    }

    function testOverUnsignedIntMaximumAge() {
        // Update the mock service to report an error.
        mock.setGeolocationPositionUnavailableError(mockMessage);
        // The maximumAge is max-value of unsigned, so we expect the cached position, not the error from the service.
        navigator.geolocation.getCurrentPosition(function(p) {
            checkPosition(p);
            testZeroMaximumAgeError();
        }, function(e) {
            testFailed('Error callback invoked unexpectedly');
            finishJSTest();
        }, {maximumAge: 4294967296});
    }

    function testZeroMaximumAgeError() {
        // The default maximumAge is zero, so we expect the error from the service.
        navigator.geolocation.getCurrentPosition(function(p) {
            testFailed('Success callback invoked unexpectedly');
            finishJSTest();
        }, function(e) {
            checkError(e);
            finishJSTest();
        });
    }
});

window.jsTestIsAsync = true;
