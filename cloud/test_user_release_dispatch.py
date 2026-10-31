"""Include the user-session release helper's regression cases in Cloud development CI.

Production workflow is unchanged; these tests use synthetic GitHub responses.
"""

from tools.release.test_dispatch_verified import DispatchTests, ReleasePreflightTests
