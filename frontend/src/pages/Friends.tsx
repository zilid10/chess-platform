import { useState, useEffect, useCallback } from 'react';
import { apiErrorMessage } from '../services/errors';
import { friendService } from '../services/friendService';
import { userService } from '../services/userService';
import { User, FriendRequest } from '../types';
import { Users, UserPlus, Inbox, Send, UserMinus, Check, X, Search } from 'lucide-react';

type TabType = 'friends' | 'received' | 'sent';

const Friends = () => {
  const [activeTab, setActiveTab] = useState<TabType>('friends');
  const [friends, setFriends] = useState<User[]>([]);
  const [receivedRequests, setReceivedRequests] = useState<FriendRequest[]>([]);
  const [sentRequests, setSentRequests] = useState<FriendRequest[]>([]);
  const [searchResults, setSearchResults] = useState<User[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  
  // Pagination state
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [pageSize] = useState(10);

  const loadData = useCallback(async (tab: TabType, page: number) => {
    setLoading(true);
    setError('');
    try {
      if (tab === 'friends') {
        const response = await friendService.getFriends(page, pageSize);
        setFriends(response.content);
        setTotalPages(response.totalPages);
        setTotalElements(response.totalElements);
      } else if (tab === 'received') {
        const response = await friendService.getReceivedRequests(page, pageSize);
        setReceivedRequests(response.content);
        setTotalPages(response.totalPages);
        setTotalElements(response.totalElements);
      } else if (tab === 'sent') {
        const response = await friendService.getSentRequests(page, pageSize);
        setSentRequests(response.content);
        setTotalPages(response.totalPages);
        setTotalElements(response.totalElements);
      }
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to load data'));
    } finally {
      setLoading(false);
    }
  }, [pageSize]);

  // Load data when tab or page changes
  useEffect(() => {
    loadData(activeTab, currentPage);
  }, [activeTab, currentPage, loadData]);

  // Reset page when switching tabs
  const handleTabChange = (newTab: TabType) => {
    if (newTab !== activeTab) {
      setCurrentPage(0);
      setActiveTab(newTab);
    }
  };

  const handleSearch = async () => {
    if (!searchQuery.trim()) {
      setSearchResults([]);
      return;
    }

    setLoading(true);
    setError('');
    try {
      const results = await userService.searchUsers(searchQuery);
      setSearchResults(results.content);
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to search users'));
    } finally {
      setLoading(false);
    }
  };

  const handleSendRequest = async (userId: string) => {
    setError('');
    try {
      await friendService.sendFriendRequest(userId);
      setSuccessMessage('Friend request sent successfully!');
      setTimeout(() => setSuccessMessage(''), 3000);
      setSearchResults([]);
      setSearchQuery('');
      // Reload sent requests if on that tab
      if (activeTab === 'sent') {
        loadData(activeTab, currentPage);
      }
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to send friend request'));
    }
  };

  const handleAcceptRequest = async (requestId: string) => {
    setError('');
    try {
      await friendService.acceptFriendRequest(requestId);
      setSuccessMessage('Friend request accepted!');
      setTimeout(() => setSuccessMessage(''), 3000);
      // Reload current data
      loadData(activeTab, currentPage);
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to accept request'));
    }
  };

  const handleRejectRequest = async (requestId: string) => {
    setError('');
    try {
      await friendService.rejectFriendRequest(requestId);
      setSuccessMessage('Friend request rejected');
      setTimeout(() => setSuccessMessage(''), 3000);
      // Reload current data
      loadData(activeTab, currentPage);
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to reject request'));
    }
  };

  const handleRemoveFriend = async (userId: string) => {
    if (!window.confirm('Are you sure you want to remove this friend?')) {
      return;
    }

    setError('');
    try {
      await friendService.removeFriend(userId);
      setSuccessMessage('Friend removed');
      setTimeout(() => setSuccessMessage(''), 3000);
      // Reload current data
      loadData(activeTab, currentPage);
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to remove friend'));
    }
  };

  const handlePageChange = (newPage: number) => {
    setCurrentPage(newPage);
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900">Friends</h1>
        <p className="mt-2 text-gray-600">Manage your friends and friend requests</p>
      </div>

      {/* Messages */}
      {error && (
        <div className="mb-6 rounded-md bg-red-50 p-4">
          <p className="text-sm text-red-800">{error}</p>
        </div>
      )}
      {successMessage && (
        <div className="mb-6 rounded-md bg-green-50 p-4">
          <p className="text-sm text-green-800">{successMessage}</p>
        </div>
      )}

      {/* Search Users Section */}
      <div className="card mb-6">
        <h2 className="text-xl font-semibold mb-4 flex items-center space-x-2">
          <UserPlus size={24} className="text-primary-600" />
          <span>Add Friends</span>
        </h2>
        <div className="flex space-x-4">
          <div className="flex-1">
            <input
              type="text"
              className="input"
              placeholder="Search users by username..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
            />
          </div>
          <button
            onClick={handleSearch}
            className="btn btn-primary flex items-center space-x-2"
          >
            <Search size={20} />
            <span>Search</span>
          </button>
        </div>

        {/* Search Results */}
        {searchResults.length > 0 && (
          <div className="mt-4 space-y-2">
            <h3 className="font-semibold text-gray-700">Search Results:</h3>
            {searchResults.map((user) => (
              <div
                key={user.id}
                className="flex items-center justify-between p-3 bg-gray-50 rounded-lg"
              >
                <div>
                  <p className="font-medium text-gray-900">{user.username}</p>
                  <p className="text-sm text-gray-600">{user.email}</p>
                </div>
                <button
                  onClick={() => handleSendRequest(user.id)}
                  className="btn btn-primary btn-sm flex items-center space-x-2"
                >
                  <UserPlus size={16} />
                  <span>Add Friend</span>
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Tabs */}
      <div className="border-b border-gray-200 mb-6">
        <nav className="-mb-px flex space-x-8">
          <button
            onClick={() => handleTabChange('friends')}
            className={`py-4 px-1 border-b-2 font-medium text-sm flex items-center space-x-2 ${
              activeTab === 'friends'
                ? 'border-primary-500 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            <Users size={20} />
            <span>Friends {activeTab === 'friends' && totalElements > 0 ? `(${totalElements})` : ''}</span>
          </button>
          <button
            onClick={() => handleTabChange('received')}
            className={`py-4 px-1 border-b-2 font-medium text-sm flex items-center space-x-2 ${
              activeTab === 'received'
                ? 'border-primary-500 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            <Inbox size={20} />
            <span>Received {activeTab === 'received' && totalElements > 0 ? `(${totalElements})` : ''}</span>
          </button>
          <button
            onClick={() => handleTabChange('sent')}
            className={`py-4 px-1 border-b-2 font-medium text-sm flex items-center space-x-2 ${
              activeTab === 'sent'
                ? 'border-primary-500 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
          >
            <Send size={20} />
            <span>Sent {activeTab === 'sent' && totalElements > 0 ? `(${totalElements})` : ''}</span>
          </button>
        </nav>
      </div>

      {/* Tab Content */}
      {loading ? (
        <div className="flex justify-center py-12">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
        </div>
      ) : (
        <>
          <div className="space-y-4">
            {/* Friends List */}
            {activeTab === 'friends' && (
              <>
                {friends.length === 0 ? (
                  <div className="text-center py-12 text-gray-500">
                    <Users size={48} className="mx-auto mb-4 opacity-50" />
                    <p>No friends yet. Search for users to add friends!</p>
                  </div>
                ) : (
                  friends.map((friend) => (
                    <div key={friend.id} className="card flex items-center justify-between">
                      <div>
                        <p className="font-medium text-gray-900">{friend.username}</p>
                        <p className="text-sm text-gray-600">{friend.email}</p>
                        {friend.about && (
                          <p className="text-sm text-gray-500 mt-1">{friend.about}</p>
                        )}
                      </div>
                      <button
                        onClick={() => handleRemoveFriend(friend.id)}
                        className="btn btn-outline flex items-center space-x-2 text-red-600 border-red-600 hover:bg-red-50"
                      >
                        <UserMinus size={16} />
                        <span>Remove</span>
                      </button>
                    </div>
                  ))
                )}
              </>
            )}

            {/* Received Requests */}
            {activeTab === 'received' && (
              <>
                {receivedRequests.length === 0 ? (
                  <div className="text-center py-12 text-gray-500">
                    <Inbox size={48} className="mx-auto mb-4 opacity-50" />
                    <p>No pending friend requests</p>
                  </div>
                ) : (
                  receivedRequests.map((request) => (
                    <div key={request.friendRequestId} className="card flex items-center justify-between">
                      <div>
                        <p className="font-medium text-gray-900">{request.sender.username}</p>
                        <p className="text-sm text-gray-600">{request.sender.email}</p>
                        <p className="text-xs text-gray-500 mt-1">
                          Sent on {new Date(request.requestedAt).toLocaleDateString()}
                        </p>
                      </div>
                      <div className="flex space-x-2">
                        <button
                          onClick={() => handleAcceptRequest(request.friendRequestId)}
                          className="btn btn-primary flex items-center space-x-2"
                        >
                          <Check size={16} />
                          <span>Accept</span>
                        </button>
                        <button
                          onClick={() => handleRejectRequest(request.friendRequestId)}
                          className="btn btn-outline flex items-center space-x-2 text-red-600 border-red-600 hover:bg-red-50"
                        >
                          <X size={16} />
                          <span>Reject</span>
                        </button>
                      </div>
                    </div>
                  ))
                )}
              </>
            )}

            {/* Sent Requests */}
            {activeTab === 'sent' && (
              <>
                {sentRequests.length === 0 ? (
                  <div className="text-center py-12 text-gray-500">
                    <Send size={48} className="mx-auto mb-4 opacity-50" />
                    <p>No sent friend requests</p>
                  </div>
                ) : (
                  sentRequests.map((request) => (
                    <div key={request.friendRequestId} className="card flex items-center justify-between">
                      <div>
                        <p className="font-medium text-gray-900">{request.recipient.username}</p>
                        <p className="text-sm text-gray-600">{request.recipient.email}</p>
                        <p className="text-xs text-gray-500 mt-1">
                          Sent on {new Date(request.requestedAt).toLocaleDateString()}
                        </p>
                      </div>
                      <div className="px-4 py-2 bg-yellow-50 text-yellow-700 rounded-md text-sm font-medium">
                        Pending
                      </div>
                    </div>
                  ))
                )}
              </>
            )}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="mt-6 flex items-center justify-center space-x-2">
              <button
                onClick={() => handlePageChange(Math.max(0, currentPage - 1))}
                disabled={currentPage === 0}
                className="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Previous
              </button>
              
              <div className="flex items-center space-x-1">
                {Array.from({ length: totalPages }, (_, i) => i).map((page) => {
                  // Show first page, last page, current page, and pages around current
                  const showPage = 
                    page === 0 || 
                    page === totalPages - 1 || 
                    Math.abs(page - currentPage) <= 1;
                  
                  const showEllipsis = 
                    (page === 1 && currentPage > 3) ||
                    (page === totalPages - 2 && currentPage < totalPages - 4);

                  if (showEllipsis) {
                    return (
                      <span key={page} className="px-2 text-gray-500">
                        ...
                      </span>
                    );
                  }

                  if (!showPage) return null;

                  return (
                    <button
                      key={page}
                      onClick={() => handlePageChange(page)}
                      className={`px-3 py-2 border rounded-md text-sm font-medium ${
                        currentPage === page
                          ? 'bg-primary-600 text-white border-primary-600'
                          : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
                      }`}
                    >
                      {page + 1}
                    </button>
                  );
                })}
              </div>

              <button
                onClick={() => handlePageChange(Math.min(totalPages - 1, currentPage + 1))}
                disabled={currentPage === totalPages - 1}
                className="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default Friends;
