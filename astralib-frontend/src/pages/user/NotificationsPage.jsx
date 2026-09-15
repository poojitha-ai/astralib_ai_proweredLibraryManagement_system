import React from 'react';
import { Bell, Info, CheckCircle2 } from 'lucide-react';
import { Badge, EmptyState } from '../../components/common/Components';

export function NotificationsPage() {
  return (
    <div className="page-container">
      {/* Formal Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>Library Notifications & Notices</h1>
          <p>System announcements, catalogue updates, and academic notifications from the AstraLib administration.</p>
        </div>
      </div>

      {/* Empty State / Clean System Placeholder */}
      <EmptyState
        icon={Bell}
        title="No Unread Notifications"
        description="You have no pending library loan notices, hold reminders, or system maintenance alerts at this time."
      />
    </div>
  );
}
